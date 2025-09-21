# iCloud Image Importer

A **Spring Boot CLI utility** that helps organize and upload photos downloaded from iCloud to a local NAS (or any WebDAV-compatible storage).

The project provides two main commands:

- **`image-sort`**  
  Reads photos from a `src` folder, extracts metadata, and sorts them into a `dest` folder.
    - Ordinary images are organized by **year/month** based on their metadata.
    - Preserves original filenames while ensuring directory structure is clean.

- **`image-upload-webdav`**  
  Reads already-sorted images from a `src` folder and uploads them to a WebDAV server.
    - Keeps folder hierarchy intact during upload.
    - Deduplicates images by the name and file size. 

---

## Features

- Automatic sorting by **year/month** from EXIF metadata.
- Uploads to any **WebDAV** endpoint (e.g., Nextcloud, NAS, or self-hosted storage).
- Runs as a **Spring Boot command-line application**.
- Configurable source and destination paths.

---

## Getting Started

### Prerequisites

- Java 17+ (recommended)
- Access to a WebDAV endpoint (for uploading)

### Build

```shell
./gradlew build
```

### Run

```shell
java -jar ./build/libs/demo-0.0.1-SNAPSHOT.jar
```
