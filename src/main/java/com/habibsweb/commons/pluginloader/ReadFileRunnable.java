/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.habibsweb.commons.pluginloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 * @author David Hamilton
 */
class ReadFileRunnable implements Runnable {

    private final File file;
    private final String className;
    private final List<Class> classes = new ArrayList<>();

    public ReadFileRunnable(File file, String className) {
        this.file = file;
        this.className = className;
    }

    @Override
    public void run() {
        try {
            ZipFile zipFile = new ZipFile(file);
            Enumeration enumeration = zipFile.entries();
            String path = "meta-inf/services/";
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) enumeration.nextElement();
                String name = entry.getName();
                if (!name.toLowerCase().startsWith(path)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (isClass(zipFile.getInputStream(entry), className)) {
                    URL[] urls = new URL[]{file.toURI().toURL()};
                    URLClassLoader loader = new URLClassLoader(urls);
                    name = name.substring(name.lastIndexOf("/") + 1);
                    Class c = loader.loadClass(name);
                    if (c != null) {
                        this.classes.add(c);
                    }
                }
            }
            zipFile.close();

        } catch (Exception e) {
        }
    }

    private boolean isClass(InputStream in, String className) {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF8"))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    reader.close();
                    break;
                }
                if (line.equalsIgnoreCase(className)) {
                    return true;
                }
            }
        } catch (Exception e) {

        }
        return false;
    }

    public List<Class> getClasses() {
        return classes;
    }
}
