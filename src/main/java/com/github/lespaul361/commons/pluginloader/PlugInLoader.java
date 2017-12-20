/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.lespaul361.commons.pluginloader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * A class to aid in loading plug-ins for a parent project using multiple
 * threads
 *
 * @author Charles Hamilton
 */
public class PlugInLoader {

    private final File dir;
    private final List<String> fileTypes = new ArrayList<>();
    private List<Class> classes = new ArrayList<>();

    /**
     * Creates a new instance of the PlugInLoader and finds files in the given
     * directory with the extension "jar"
     *
     * @param dir the directory to search for <code>plugins</code>
     * @return a new PlugInLoader
     */
    public static PlugInLoader createInstance(File dir) {
        return new PlugInLoader(dir, Arrays.asList(new String[]{"jar"}));
    }

    /**
     * Creates a new instance of the PlugInLoader and finds files in the given
     * directory with the extensions supplied in the supplied list
     *
     * @param dir the directory to search for <code>plugins</code>
     * @param fileExtensions list of file extensions
     * @return a new PlugInLoader
     */
    public static PlugInLoader createInstance(File dir, List<String> fileExtensions) {
        return new PlugInLoader(dir, fileExtensions);
    }

    PlugInLoader(File dir, List<String> fileExtensions) {
        if (!dir.isDirectory()) {
            dir = dir.getParentFile();
        }
        this.dir = dir;
        if (fileExtensions == null || fileExtensions.isEmpty()) {
            fileTypes.add("jar");
        } else {
            for (String ext : fileExtensions) {
                if (ext.startsWith(".")) {
                    fileTypes.add(ext.substring(1, ext.length()));
                } else {
                    fileTypes.add(ext);
                }
            }
        }
    }

    /**
     * Finds all the files of the type specified
     *
     * @param completeClassName the name of the class to look for. Example:
     * com.package.interface
     */
    public void load(String completeClassName) {
        List<File> files = getFiles();
        List<Runnable> runnables = new ArrayList<>();
        for (File file : files) {
            runnables.add(new ReadFileRunnable(file, completeClassName));
        }
        MyThreadPool pool = new MyThreadPool(5);
        pool.execute(runnables);
        pool.waitUntilDone();
        for (Runnable r : runnables) {
            ReadFileRunnable rfr = (ReadFileRunnable) r;
            classes.addAll(rfr.getClasses());
        }

        System.gc();
    }

    /**
     * Gets the <code>Enumerator</code> for the collection of classes
     *
     * @return enumerator
     */
    public Enumeration enumerator() {
        return new PlugInEnumerator(classes);
    }

    /**
     * Sets the file extensions to looks for. Default is jar.
     *
     * @param extensions An array of file extensions
     */
    public void setFileTypes(String[] extensions) {
        fileTypes.clear();
        for (String ext : extensions) {
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            fileTypes.add(ext);
        }
    }

    private List<File> getFiles() {
        File[] files = dir.listFiles();
        List<File> ret = new ArrayList<>();
        for (File fl : files) {
            for (String ext : fileTypes) {
                if (fl.getName().toLowerCase().endsWith(ext)) {
                    ret.add(fl);
                    break;
                }
            }
        }
        return ret;
    }
}

class MyThreadPool {

    private final List<Thread> pendingThreads = new ArrayList<>();
    private final List<Thread> activeThreads = new ArrayList<>();
    private final int maxThreads;
    private boolean isDone = false;
    private boolean isRunning = false;

    public MyThreadPool(int maxThreads) {
        if (maxThreads < 1) {
            maxThreads = 1;
        }
        this.maxThreads = maxThreads;
    }

    public synchronized void execute(Runnable r) {
        synchronized (pendingThreads) {
            pendingThreads.add(new Thread(r));
        }
        if (!isRunning) {
            start();
        }
    }

    public void execute(Collection<Runnable> r) {
        synchronized (pendingThreads) {
            for (Runnable runnable : r) {
                pendingThreads.add(new Thread(runnable));
            }
        }
        if (!isRunning) {
            start();
        }
    }

    private void start() {
        isRunning = true;
        class PR implements Runnable {

            @Override
            public void run() {
                checkThreads();
                while (!isDone) {
                    try {
                        Thread.currentThread().sleep(100);
                        checkThreads();
                    } catch (Exception e) {
                    }
                }

            }

            private void checkThreads() {
                synchronized (activeThreads) {
                    Iterator<Thread> it = activeThreads.iterator();
                    while (it.hasNext()) {
                        Thread thr = it.next();
                        if (!thr.isAlive()) {
                            it.remove();
                        }
                    }
                }

                if (activeThreads.size() < maxThreads) {
                    if (!pendingThreads.isEmpty()) {
                        for (int c = activeThreads.size(); c < maxThreads; c++) {
                            Thread thr = null;
                            synchronized (pendingThreads) {
                                thr = pendingThreads.get(0);
                                pendingThreads.remove(0);
                            }
                            synchronized (activeThreads) {
                                thr.start();
                                activeThreads.add(thr);
                            }
                        }
                    }
                }

                if (pendingThreads.isEmpty() && activeThreads.isEmpty()) {
                    isDone = true;
                }

            }

        }
        new Thread(new PR()).start();
    }

    public void waitUntilDone() {
        while (!isDone) {
            try {
                Thread.currentThread().sleep(100);
            } catch (Exception e) {
            }

        }
    }

}

class PlugInEnumerator implements Enumeration<Class> {

    private final List<Class> classes;
    private int index = 0;

    PlugInEnumerator(List<Class> classes) {
        this.classes = classes;
    }

    @Override
    public boolean hasMoreElements() {
        return index > classes.size() - 2;
    }

    @Override
    public Class nextElement() {
        if (hasMoreElements()) {
            index++;
            return classes.get(index);

        }
        return null;
    }
}
