# VideoUpload

## Architecture

Compose UI + ViewModel per screen (`videolist`, `record`, `videodetail`), Koin DI, SQLDelight for storage, CameraX for recording, Media3 for playback.

The DB is the single source of truth for upload state: `UploadManager` only writes to the DB, the UI just observes flows. List and detail stay in sync and restart recovery is a one-line SQL update.

## Upload simulation

`UploadManager` advances `uploadProgress` by 5% every 200 ms (4 s total). 50% chance to fail at a random point between 20–80%. Cancel returns the row to `RECORDED`; retry from `FAILED` starts again from 0.

## Restart recovery

Any video recording left in `UPLOADING` at app start is moved to `FAILED`.

The upload is in-process, no bytes survive a kill and nothing to resume. `FAILED` is the honest signal and the user retries via the existing button.

## Time spent

≈ 10 hours.

## For production

- More comprehensive camera implementation 
- Foreground service for uploads

## Extras

Unit tests: `./gradlew :app:testDebugUnitTest`
Spotless: `./gradlew spotlessApply`