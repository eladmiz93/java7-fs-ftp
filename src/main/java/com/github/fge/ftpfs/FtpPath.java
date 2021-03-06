/*
 * Copyright (c) 2014, Francis Galiegue (fgaliegue@gmail.com)
 *
 * This software is dual-licensed under:
 *
 * - the Lesser General Public License (LGPL) version 3.0 or, at your option, any
 *   later version;
 * - the Apache Software License (ASL) version 2.0.
 *
 * The text of both licenses is available under the src/resources/ directory of
 * this project (under the names LGPL-3.0.txt and ASL-2.0.txt respectively).
 *
 * Direct link to the sources:
 *
 * - LGPL 3.0: https://www.gnu.org/licenses/lgpl-3.0.txt
 * - ASL 2.0: http://www.apache.org/licenses/LICENSE-2.0.txt
 */

package com.github.fge.ftpfs;

import com.github.fge.ftpfs.path.SlashPath;
import com.github.fge.ftpfs.watch.NopWatchKey;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;

public final class FtpPath
    implements Path
{
    private final FileSystem fs;
    private final URI uri;
    private final SlashPath path;

    public FtpPath(final FileSystem fs, final URI uri, final SlashPath path)
    {
        this.fs = fs;
        this.uri = uri;
        this.path = path;
    }

    @Override
    public FileSystem getFileSystem()
    {
        return fs;
    }

    @Override
    public boolean isAbsolute()
    {
        return path.isAbsolute();
    }

    @Override
    public Path getRoot()
    {
        return isAbsolute() ? new FtpPath(fs, uri, SlashPath.ROOT) : null;
    }

    @Override
    public Path getFileName()
    {
        final SlashPath name = path.getLastName();
        return name == null ? null : new FtpPath(fs, uri, name);
    }

    @Override
    public Path getParent()
    {
        final SlashPath parent = path.getParent();
        return parent == null ? null : new FtpPath(fs, uri, parent);
    }

    @Override
    public int getNameCount()
    {
        return path.getNameCount();
    }

    @Override
    public Path getName(final int index)
    {
        return new FtpPath(fs, uri, path.getName(index));
    }

    @Override
    public Path subpath(final int beginIndex, final int endIndex)
    {
        return new FtpPath(fs, uri, path.subpath(beginIndex, endIndex));
    }

    @Override
    public boolean startsWith(final Path other)
    {
        if (!fs.equals(other.getFileSystem()))
            return false;
        final FtpPath otherPath = (FtpPath) other;
        return path.startsWith(otherPath.path);
    }

    @Override
    public boolean startsWith(final String other)
    {
        return startsWith(new FtpPath(fs, uri, SlashPath.fromString(other)));
    }

    @Override
    public boolean endsWith(final Path other)
    {
        if (!fs.equals(other.getFileSystem()))
            return false;
        final FtpPath otherPath = (FtpPath) other;
        return path.endsWith(otherPath.path);
    }

    @Override
    public boolean endsWith(final String other)
    {
        return endsWith(new FtpPath(fs, uri, SlashPath.fromString(other)));
    }

    @Override
    public Path normalize()
    {
        return path.isNormalized() ? this
            : fs.getPath(path.normalize().toString());
    }

    @Override
    public Path resolve(final Path other)
    {
        if (!fs.provider().equals(other.getFileSystem().provider()))
            throw new ProviderMismatchException();
        if (other.isAbsolute())
            return other;
        final FtpPath otherPath = (FtpPath) other;
        return SlashPath.EMPTY.equals(otherPath.path) ? this
            : new FtpPath(fs, uri, path.resolve(otherPath.path));
    }

    @Override
    public Path resolve(final String other)
    {
        return new FtpPath(fs, uri, path.resolve(SlashPath.fromString(other)));
    }

    @Override
    public Path resolveSibling(final Path other)
    {
        if (!fs.provider().equals(other.getFileSystem().provider()))
            throw new ProviderMismatchException();
        final SlashPath parent = path.getParent();
        if (parent == null)
            return other;
        final FtpPath otherPath = (FtpPath) other;
        return new FtpPath(fs, uri, parent.resolve(otherPath.path));
    }

    @Override
    public Path resolveSibling(final String other)
    {
        final FtpPath otherPath
            = new FtpPath(fs, uri, SlashPath.fromString(other));
        return resolveSibling(otherPath);
    }

    @Override
    public Path relativize(final Path other)
    {
        if (!fs.provider().equals(other.getFileSystem().provider()))
            throw new ProviderMismatchException();
        final FtpPath otherPath = (FtpPath) other;
        return new FtpPath(fs, uri, path.relativize(otherPath.path));
    }

    @Override
    public URI toUri()
    {
        /*
         * This is a strange one...
         *
         * As an FTP user you have a root, and cannot go above it. We therefore
         * normalize the path first, then remove all leading dot-dots.
         */
        final SlashPath normalized = path.normalize();
        return URI.create(uri.toString() + stripDotDots(normalized));
    }

    @Override
    public Path toAbsolutePath()
    {
        /*
         * TODO: fix that...
         *
         * Can we have a non absolute path anyway?
         */
        return isAbsolute() ? this : new FtpPath(fs, uri, stripDotDots(path));
    }

    @Override
    public Path toRealPath(final LinkOption... options)
        throws IOException
    {
        // TODO: symlink support
        return toAbsolutePath();
    }

    @Override
    public File toFile()
    {
        return new File(path.toString());
    }

    @Override
    public WatchKey register(final WatchService watcher,
        final WatchEvent.Kind<?>[] events,
        final WatchEvent.Modifier... modifiers)
        throws IOException
    {
        return new NopWatchKey();
    }

    @Override
    public WatchKey register(final WatchService watcher,
        final WatchEvent.Kind<?>... events)
        throws IOException
    {
        return new NopWatchKey();
    }

    @Override
    public Iterator<Path> iterator()
    {
        return new Iterator<Path>()
        {
            private final Iterator<String> it = path.iterator();

            @Override
            public boolean hasNext()
            {
                return it.hasNext();
            }

            @Override
            public Path next()
            {
                return fs.getPath(it.next());
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public int compareTo(final Path other)
    {
        return path.toString().compareTo(other.toString());
    }

    @Override
    public int hashCode()
    {
        return 31 * uri.hashCode() + path.hashCode();
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (obj == null)
            return false;
        if (this == obj)
            return true;
        if (getClass() != obj.getClass())
            return false;
        final FtpPath other = (FtpPath) obj;
        return uri.equals(other.uri) && path.equals(other.path);
    }

    @Override
    public String toString()
    {
        return path.toString();
    }

    private static SlashPath stripDotDots(final SlashPath path)
    {
        final int len = path.getNameCount();
        int i = 0;
        final Iterator<String> iterator = path.iterator();
        while (iterator.hasNext() && "..".equals(iterator.next()))
            i++;
        return SlashPath.ROOT.resolve(path.subpath(i, len));
    }
}
