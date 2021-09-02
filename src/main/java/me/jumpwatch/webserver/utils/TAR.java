package me.jumpwatch.webserver.utils;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class TAR {


    /**
     * Extract a "tar.gz" file into a given folder.
     * @param file
     * @param folder
     */
    public static void extractTarArchive(File file, String folder) throws IOException {
        //logger.info("Extracting archive {} into folder {}", file.getName(), folder);
        // @formatter:off
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GzipCompressorInputStream gzip = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            // @formatter:on
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null) {
                extractEntry(entry, tar, folder);
            }
        }
        //logger.info("Archive extracted");
    }

    /**
     * Extract an entry of the input stream into a given folder
     * @param entry
     * @param tar
     * @param folder
     * @throws IOException
     */
    public static void extractEntry(ArchiveEntry entry, InputStream tar, String folder) throws IOException {
        final int bufferSize = 4096;
        final String path = folder + entry.getName();
        if (entry.isDirectory()) {
            new File(path).mkdirs();
        } else {
            int count;
            byte[] data = new byte[bufferSize];
            // @formatter:off
            try (FileOutputStream os = new FileOutputStream(path);
                 BufferedOutputStream dest = new BufferedOutputStream(os, bufferSize)) {
                // @formatter:off
                while ((count = tar.read(data, 0, bufferSize)) != -1) {
                    dest.write(data, 0, count);
                }
            }
        }
    }
}
