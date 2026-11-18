package io.github.tejesh_zephaniah.folio.reader.components

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class PageCurlGLRenderer : GLSurfaceView.Renderer {
    private val pageTextures = mutableMapOf<Int, Int>()
    private val uploadedBitmaps = mutableMapOf<Int, Bitmap>()
    private var sourceBitmaps: Map<Int, Bitmap> = emptyMap()
    private var currentPage = 1
    private var totalPages = 1
    private var width = 1f
    private var height = 1f
    private var mesh: PageCurlMesh? = null
    private var turnDirection = TurnDirection.FORWARD
    private var turnFrontPage = 1
    private var turnBackPage = 1
    private var turnUnderPage = 1
    private var turnActive = false
    private var corner = PageTurnCorner.BOTTOM_RIGHT
    private var pointer = Pair(1f, 1f)
    private var program = 0
    private var positionAttribute = 0
    private var normalAttribute = 0
    private var textureAttribute = 0
    private var foldAttribute = 0
    private var frontSampler = 0
    private var backSampler = 0
    private var hasBackSampler = 0
    private var foldActiveUniform = 0
    private var bufferedCurlMesh: PageCurlMesh? = null
    private var curlVertexBuffer: FloatBuffer? = null
    private var curlIndexBuffer: ShortBuffer? = null
    private var flatVertexBuffer: FloatBuffer? = null
    private var flatIndexBuffer: ShortBuffer? = null
    private var flatMesh: PageCurlMesh? = null

    @Volatile
    var isAvailable: Boolean = false
        private set

    fun beginTurn(direction: TurnDirection, turnCorner: PageTurnCorner, x: Float, y: Float) {
        turnDirection = direction
        turnActive = when (direction) {
            TurnDirection.FORWARD -> currentPage < totalPages
            TurnDirection.BACKWARD -> currentPage > 1
        }
        if (!turnActive) return

        corner = turnCorner
        pointer = Pair(x, y)
        if (direction == TurnDirection.FORWARD) {
            turnFrontPage = currentPage
            turnBackPage = currentPage
            turnUnderPage = (currentPage + 1).coerceAtMost(totalPages)
        } else {
            turnFrontPage = (currentPage - 1).coerceAtLeast(1)
            turnBackPage = (currentPage - 1).coerceAtLeast(1)
            turnUnderPage = currentPage
        }
        updateMesh()
    }

    fun updateTurn(x: Float, y: Float) {
        if (!turnActive) return
        pointer = Pair(x, y)
        updateMesh()
    }

    fun cancelTurn() {
        turnActive = false
        mesh = null
    }

    fun commitPage(page: Int) {
        currentPage = page.coerceIn(1, totalPages)
        turnActive = false
        mesh = null
    }

    fun updatePageData(bitmaps: Map<Int, Bitmap>, page: Int, pages: Int) {
        sourceBitmaps = bitmaps
        totalPages = pages.coerceAtLeast(1)
        currentPage = page.coerceIn(1, totalPages)
        if (!turnActive) mesh = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        pageTextures.clear()
        uploadedBitmaps.clear()
        GLES20.glClearColor(0.94f, 0.92f, 0.88f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) {
            isAvailable = false
            return
        }
        positionAttribute = GLES20.glGetAttribLocation(program, "aPosition")
        normalAttribute = GLES20.glGetAttribLocation(program, "aNormal")
        textureAttribute = GLES20.glGetAttribLocation(program, "aTexCoord")
        foldAttribute = GLES20.glGetAttribLocation(program, "aFold")
        frontSampler = GLES20.glGetUniformLocation(program, "uFrontTexture")
        backSampler = GLES20.glGetUniformLocation(program, "uBackTexture")
        hasBackSampler = GLES20.glGetUniformLocation(program, "uHasBack")
        foldActiveUniform = GLES20.glGetUniformLocation(program, "uFoldActive")
        isAvailable = true
        uploadAvailableTextures()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1).toFloat()
        this.height = height.coerceAtLeast(1).toFloat()
        GLES20.glViewport(0, 0, width, height)
        flatMesh = PageCurlGeometry.flatPage(this.width, this.height)
        flatVertexBuffer = null
        flatIndexBuffer = null
        if (turnActive) updateMesh()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!isAvailable) return

        uploadAvailableTextures()
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(frontSampler, 0)
        GLES20.glUniform1i(backSampler, 1)

        val canDraw = when (turnDirection) {
            TurnDirection.FORWARD -> currentPage < totalPages
            TurnDirection.BACKWARD -> currentPage > 1
        }
        if (turnActive && canDraw) {
            drawFlatPage(turnUnderPage)
            mesh?.let { drawMesh(it, turnFrontPage, turnBackPage, true) }
        } else {
            drawFlatPage(currentPage)
        }
    }

    private fun updateMesh() {
        mesh = PageCurlGeometry.create(width, height, pointer, corner)
    }

    private fun drawFlatPage(page: Int) {
        drawMesh(flatMesh ?: return, page, page, false)
    }

    private fun drawMesh(mesh: PageCurlMesh, frontPage: Int, backPage: Int, folded: Boolean) {
        val frontTexture = pageTextures[frontPage] ?: return
        val backTexture = pageTextures[backPage] ?: frontTexture
        val vertices = mesh.vertices
        val indices = mesh.indices
        val buffers = if (folded) curlBuffersFor(mesh) else flatBuffersFor(mesh)
        val vertexBuffer = buffers.first
        val indexBuffer = buffers.second

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frontTexture)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backTexture)
        GLES20.glUniform1i(hasBackSampler, if (folded) 1 else 0)
        GLES20.glUniform1i(foldActiveUniform, if (folded) 1 else 0)

        val stride = FLOATS_PER_VERTEX * Float.SIZE_BYTES
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, stride, vertexBuffer)
        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(normalAttribute)
        GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, stride, vertexBuffer)
        vertexBuffer.position(6)
        GLES20.glEnableVertexAttribArray(textureAttribute)
        GLES20.glVertexAttribPointer(textureAttribute, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)
        vertexBuffer.position(8)
        GLES20.glEnableVertexAttribArray(foldAttribute)
        GLES20.glVertexAttribPointer(foldAttribute, 1, GLES20.GL_FLOAT, false, stride, vertexBuffer)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glDisableVertexAttribArray(normalAttribute)
        GLES20.glDisableVertexAttribArray(textureAttribute)
        GLES20.glDisableVertexAttribArray(foldAttribute)
    }

    private fun uploadAvailableTextures() {
        if (!isAvailable || sourceBitmaps.isEmpty()) return

        sourceBitmaps.forEach { (page, bitmap) ->
            if (uploadedBitmaps[page] !== bitmap) {
                pageTextures[page]?.let { previous ->
                    GLES20.glDeleteTextures(1, intArrayOf(previous), 0)
                }
                val texture = IntArray(1)
                GLES20.glGenTextures(1, texture, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                pageTextures[page] = texture[0]
                uploadedBitmaps[page] = bitmap
            }
        }

        val keepPages = sourceBitmaps.keys + currentPage + if (turnActive) {
            listOf(turnFrontPage, turnBackPage, turnUnderPage)
        } else {
            emptyList()
        }
        val expired = pageTextures.keys - keepPages.toSet()
        if (expired.isNotEmpty()) {
            val ids = expired.mapNotNull(pageTextures::get).toIntArray()
            GLES20.glDeleteTextures(ids.size, ids, 0)
            expired.forEach {
                pageTextures.remove(it)
                uploadedBitmaps.remove(it)
            }
        }
    }

    private fun curlBuffersFor(mesh: PageCurlMesh): Pair<FloatBuffer, ShortBuffer> {
        if (bufferedCurlMesh !== mesh) {
            if (curlVertexBuffer?.capacity() != mesh.vertices.size) {
                curlVertexBuffer = ByteBuffer.allocateDirect(mesh.vertices.size * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            }
            if (curlIndexBuffer?.capacity() != mesh.indices.size) {
                curlIndexBuffer = ByteBuffer.allocateDirect(mesh.indices.size * Short.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
            }
            curlVertexBuffer?.let { buffer ->
                buffer.clear()
                buffer.put(mesh.vertices)
                buffer.position(0)
            }
            curlIndexBuffer?.let { buffer ->
                buffer.clear()
                buffer.put(mesh.indices)
                buffer.position(0)
            }
            bufferedCurlMesh = mesh
        }
        return Pair(checkNotNull(curlVertexBuffer), checkNotNull(curlIndexBuffer))
    }

    private fun flatBuffersFor(mesh: PageCurlMesh): Pair<FloatBuffer, ShortBuffer> {
        if (flatVertexBuffer == null || flatIndexBuffer == null) {
            flatVertexBuffer = ByteBuffer.allocateDirect(mesh.vertices.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mesh.vertices)
                .apply { position(0) }
            flatIndexBuffer = ByteBuffer.allocateDirect(mesh.indices.size * Short.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .put(mesh.indices)
                .apply { position(0) }
        }
        return Pair(checkNotNull(flatVertexBuffer), checkNotNull(flatIndexBuffer))
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == 0 || fragmentShader == 0) return 0
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertexShader)
        GLES20.glAttachShader(result, fragmentShader)
        GLES20.glLinkProgram(result)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        if (status[0] == 0) {
            Log.e(TAG, "Failed to link the page-curl shader program.")
            GLES20.glDeleteProgram(result)
            return 0
        }
        return result
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Failed to compile a page-curl shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val TAG = "PageCurlRenderer"
        private const val FLOATS_PER_VERTEX = 9

        private const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;
            attribute float aFold;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            varying float vFold;
            void main() {
                gl_Position = vec4(aPosition.x, aPosition.y, aPosition.z * 0.5, 1.0);
                vNormal = aNormal;
                vTexCoord = aTexCoord;
                vFold = aFold;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uFrontTexture;
            uniform sampler2D uBackTexture;
            uniform int uHasBack;
            uniform int uFoldActive;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            varying float vFold;
            void main() {
                vec2 uv = vTexCoord;
                if (!gl_FrontFacing) {
                    uv.x = 1.0 - uv.x;
                }
                vec4 pageColor;
                if (uHasBack == 1 && !gl_FrontFacing) {
                    pageColor = texture2D(uBackTexture, uv);
                } else {
                    pageColor = texture2D(uFrontTexture, uv);
                }
                vec3 normal = normalize(vNormal);
                if (!gl_FrontFacing) {
                    normal = -normal;
                }
                vec3 lightDirection = normalize(vec3(-0.42, 0.58, 0.70));
                float diffuse = 0.84 + 0.16 * max(dot(normal, lightDirection), 0.0);
                float creaseShadow = smoothstep(0.03, 0.24, vFold) *
                    (1.0 - smoothstep(0.70, 1.0, vFold));
                float creaseHighlight = sin(vFold * 3.14159265);
                float shadowStrength = float(uFoldActive);
                pageColor.rgb *= diffuse - creaseShadow * 0.14 * shadowStrength +
                    creaseHighlight * 0.08 * shadowStrength;
                gl_FragColor = pageColor;
            }
        """
    }
}
