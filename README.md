# TinyNovelWriter

An Android app for reading and editing [novelWriter](https://novelwriter.io/) projects, 
designed to work in tandem with the desktop version to write on the go.

This is a personal project, not affiliated with novelWriter, and not on Google Play.

## Requirements
novelWriter 26.2+ and Android 11+

## Quick start

1. Download the project folder (the one containing `nwProject.nwx`) to your phone.
2. Open the app, allow "All files access", and choose the project folder.
3. Write! Tap a document, then Edit. It saves by itself as you go.
4. Back on the computer, upload and open the project in novelWriter. It
   will say it's rebuilding the index; that's expected and picks up your phone edits.

Optional: to sync from inside the app, go to Settings → Use FolderSync link and paste the
folderpair's Sync link (requires [FolderSync](https://foldersync.io/) Pro).

## Good to know

- It only edits the text of existing documents. To add a chapter, write it into an existing
  document and split it in novelWriter.
- If a document changed on the computer while you were editing, it asks which version to
  keep instead of overwriting.
- Each phone save leaves a backup next to the document, `content/<handle>.md.bkp`. This is added because novelWriter sometimes silently overwrites open files.
- Word counts in the tree are updated only after novelWriter rebuilds its index.
- No undo yet.
- Markup highlighting, `--`/`---` dashes, Ctrl+B / Ctrl+I and typewriter scrolling work as in
  novelWriter.

## License

GPL-3.0-or-later, see [LICENSE.md](LICENSE.md).

Includes code ported from [novelWriter](https://github.com/saga-soft/novelWriter)
(Copyright (C) 2018 Veronica Berglyd Olsen and novelWriter contributors, GPL-3.0-or-later),
marked in the source with `Source: novelWriter …` comments. Icons from
[Material Icons](https://github.com/google/material-design-icons) (Apache 2.0); app icon
lettering in Pridi (SIL Open Font License 1.1).
