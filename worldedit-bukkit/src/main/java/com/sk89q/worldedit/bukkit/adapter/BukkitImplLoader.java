/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.bukkit.adapter;

import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.util.io.Closer;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads Bukkit implementation adapters.
 */
public class BukkitImplLoader {

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    private final List<String> adapterCandidates = new ArrayList<>();
    private String customCandidate;

    private static final String SEARCH_PACKAGE = "com.sk89q.worldedit.bukkit.adapter.impl";
    private static final String SEARCH_PACKAGE_DOT = SEARCH_PACKAGE + ".";
    private static final String SEARCH_PATH = SEARCH_PACKAGE.replace(".", "/");
    private static final String CLASS_SUFFIX = ".class";

    private static final String LOAD_ERROR_MESSAGE =
        """

            **********************************************
            ** This WorldEdit version does not fully support your version of Bukkit.
            **
            ** When working with blocks or undoing, chests will be empty, signs
            ** will be blank, and so on. There will be no support for entity
            ** and block property-related functions.
            **
            ** Please see https://worldedit.enginehub.org/en/latest/faq/#bukkit-adapters
            **********************************************
            """;

    /**
     * Create a new instance.
     */
    public BukkitImplLoader() {
        addDefaults();
    }

    /**
     * Add default candidates, such as any defined with
     * {@code -Dworldedit.bukkit.adapter}.
     */
    private void addDefaults() {
        String className = System.getProperty("worldedit.bukkit.adapter");
        if (className != null) {
            customCandidate = className;
            adapterCandidates.add(className);
            LOGGER.info("-Dworldedit.bukkit.adapter used to add " + className + " to the list of available Bukkit adapters");
        }
    }

    /**
     * Search the given JAR for candidate implementations.
     *
     * @param file the file
     * @throws IOException thrown on I/O error
     */
    public void addFromJar(File file) throws IOException {
        Closer closer = Closer.create();
        try (closer) {
            JarFile jar = closer.register(new JarFile(file));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();

                String className = jarEntry.getName().replaceAll("[/\\\\]+", ".");

                if (!className.startsWith(SEARCH_PACKAGE_DOT) || jarEntry.isDirectory()) {
                    continue;
                }

                int beginIndex = 0;
                int endIndex = className.length() - CLASS_SUFFIX.length();
                className = className.substring(beginIndex, endIndex);
                adapterCandidates.add(className);
            }
        }
    }

    /**
     * Search for classes stored as separate files available via the given
     * class loader.
     *
     * @param classLoader the class loader
     * @throws IOException thrown on error
     */
    public void addFromPath(ClassLoader classLoader) throws IOException {
        Enumeration<URL> resources = classLoader.getResources(SEARCH_PATH);
        while (resources.hasMoreElements()) {
            File file = new File(resources.nextElement().getFile());
            addFromPath(file);
        }
    }

    /**
     * Search for classes stored as separate files available via the given
     * path.
     *
     * @param file the path
     */
    private void addFromPath(File file) {
        String resource = SEARCH_PACKAGE_DOT + file.getName();
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    addFromPath(child);
                }
            }
        } else if (resource.endsWith(CLASS_SUFFIX)) {
            int beginIndex = 0;
            int endIndex = resource.length() - CLASS_SUFFIX.length();
            String className = resource.substring(beginIndex, endIndex);
            adapterCandidates.add(className);
        }
    }

    /**
     * Iterate through the list of candidates and load an adapter.
     *
     * @return an adapter
     * @throws AdapterLoadException thrown if no adapter could be found
     */
    public BukkitImplAdapter loadAdapter() throws AdapterLoadException {
        for (String className : adapterCandidates) {
            try {
                Class<?> cls = Class.forName(className);
                if (cls.isSynthetic()) {
                    continue;
                }
                if (BukkitImplAdapter.class.isAssignableFrom(cls)) {
                    return (BukkitImplAdapter) cls.getConstructor().newInstance();
                }
            } catch (ClassNotFoundException e) {
                LOGGER.warn("Failed to load the Bukkit adapter class '" + className
                    + "' that is not supposed to be missing", e);
            } catch (IllegalAccessException e) {
                LOGGER.warn("Failed to load the Bukkit adapter class '" + className
                    + "' that is not supposed to be raising this error", e);
            } catch (Throwable e) {
                if (className.equals(customCandidate)) {
                    LOGGER.warn("Failed to load the Bukkit adapter class '" + className + "'", e);
                }
            }
        }

        throw new AdapterLoadException(LOAD_ERROR_MESSAGE);
    }

}
