# Folio maintenance continuation

## Crash investigation

Rotating the emulator with an EPUB open reproduced a fatal `Fragment.InstantiationException` in the original APK. Android's default fragment factory tried to restore a Readium navigator without its publication or its Compose container. Folio now uses Readium's restoration factories and returns to the library after activity recreation. Reopening the book restores its saved locator. Folio also removes the live navigator before closing the publication and logs the attach and release of each reader session.

## Files changed

Production paths are relative to the Folio Kotlin package.

- `MainActivity.kt` and `FolioApp.kt` handle restoration and library history.
- `reader/ReaderScreen.kt` and `reader/components/{ReadiumReaderContainer,ReaderTopBar,ReaderBottomBar,ReaderSearchSheet}.kt` handle the reader lifecycle, selection menu, page layout, and search iterator.
- `core/storage/{ReadiumManager,BookImporter,BookRepository,FolioDatabaseHelper,PreferencesManager}.kt` manage publication resources and book deletion.
- `bookshelf/{BookshelfScreen,BookshelfStateHolder}.kt`, `bookshelf/components/{BookOverflowBottomSheet,SearchFilterBar}.kt`, and `search/SearchScreen.kt` provide book removal and search UI refinements.
- `ExampleUnitTest.kt` and `ReaderThemeAndLaunchTest.kt` use explicit local-library fixtures.

## Verification

The debug APK built and all 17 unit tests passed. Emulator checks covered EPUB and PDF rendering, book switching, rotation, relaunch, EPUB position recovery, native selection actions, persisted highlights and bookmarks, light and dark reading surfaces, and the library, Search tab, and in-book searches. The sample PDF stayed removed after relaunch and the source file's SHA-256 hash was unchanged. Logcat showed the original reproduction trace and no new Folio fatal in these runs.

## EPUB page turning

Paginated EPUBs now use the existing curl geometry in a Canvas overlay. A snapshot textures the turning page while Readium renders the next or previous page underneath. The saved location updates on commit. A cancelled drag restores the original page. Emulator checks covered both directions, cancellation, a chapter boundary, text selection, progress after relaunch, and standard PDF navigation. Physical-device frame timing and older Android rendering remain unverified.
