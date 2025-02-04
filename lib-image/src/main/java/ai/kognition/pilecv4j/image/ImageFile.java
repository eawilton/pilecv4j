/***********************************************************************
 * Legacy Film to DVD Project
 * Copyright (C) 2005 James F. Carroll
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 ****************************************************************************/

package ai.kognition.pilecv4j.image;

import static org.opencv.imgcodecs.Imgcodecs.IMREAD_UNCHANGED;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageFile {
    static {
        CvMat.initOpenCv();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageFile.class);

    /**
     * <p>
     * Read a {@link BufferedImage} from a file.
     * </p>
     *
     * <p>
     * This read method will fall back to OpenCV's codecs if the ImageIO codecs
     * don't support the requested
     * file.
     * </p>
     *
     * <p>
     * It should be noted that, if the routing falls back to using ImageIO to open
     * the file, then
     * a the data will be copied into the {@link BufferedImage} after it's loaded
     * into a {@link CvMat}.
     * </p>
     *
     * @return a new {@link BufferedImage} constructed from the decoded file
     *         contents.
     */
    public static BufferedImage readBufferedImageFromFile(final String filename) throws IOException {
        return readBufferedImageFromFile(filename, 0);
    }

    /**
     * See {@link ImageFile#readBufferedImageFromFile(String)}. ImageIO can handle
     * file formats that allow
     * multiple images in a single file such as TIFF. The default is to read the
     * first image but you can
     * ask for subsequent images by passing the imageNumber (starting at zero).
     *
     * <p>
     * If the imageNumber is more than the index of the last image in the file, then
     * you'll get an
     * {@link IndexOutOfBoundsException}.
     * </p>
     */
    public static BufferedImage readBufferedImageFromFile(final String filename, final int imageNumber) throws IOException, IndexOutOfBoundsException {
        LOGGER.trace("Reading BufferedImage from {}", filename);
        return doReadBufferedImageFromFile(filename, true, imageNumber);
    }

    /**
     * <p>
     * Read a {@link CvMat} from a file. You should make sure this is assigned in a try-with-resource
     * or the CvMat will leak.
     * </p>
     *
     * <p>
     * This read method is much more robust than the one supplied with OpenCv since it will couple
     * ImageIO codecs with OpenCV's codecs to provide a much wider set of formats that can be handled.
     * It should be noted that, if the routing falls back to using ImageIO to open the file, then
     * a the data will be copied into the {@link CvMat} after it's loaded into a {@link BufferedImage}.
     * </p>
     *
     * @return a new {@link CvMat} constructed from the decoded file contents.
     *         <b>Note: The caller owns the CvMat returned</b>
     */
    public static CvMat readMatFromFile(final String filename, final int mode) throws IOException {
        return doReadMatFromFile(filename, true, mode);
    }

    public static CvMat readMatFromFile(final String filename) throws IOException {
        return readMatFromFile(filename, IMREAD_UNCHANGED);
    }

    public static void writeImageFile(final BufferedImage ri, final String filename) throws IOException {
        if(!doWrite(ri, filename)) {
            LOGGER.debug("Failed to write '" + filename + "' using ImageIO");
            try (CvMat mat = Utils.img2CvMat(ri);) {
                if(!doWrite(mat, filename, true))
                    throw new IllegalArgumentException("Failed to write");
            }
        }
    }

    public static void writeImageFile(final Mat ri, final String filename) throws IOException {
        if(!doWrite(ri, filename, false)) {
            LOGGER.debug("Failed to write '" + filename + "' using OpenCV");
            final BufferedImage bi = Utils.mat2Img(ri);
            if(!doWrite(bi, filename))
                throw new IllegalArgumentException("Failed to write");
        }
    }

    public static void transcode(BufferedImage bi, final ImageDestinationDefinition dest) throws IOException {
        if(infile != null && infile.equalsIgnoreCase(dest.outfile))
            throw new IOException("Can't overwrite original file durring transcode (" + infile + ").");

        if(dest.maxw != -1 || dest.maxh != -1 || dest.maxe != -1) {
            final int width = bi.getWidth();
            final int height = bi.getHeight();

            final double scale = scale(width, height, dest);

            if(scale >= 0.0) {

                final int newwidth = (int)Math.round(scale * (width));
                final int newheight = (int)Math.round(scale * (height));

                bi = convert(bi.getScaledInstance(newwidth, newheight, BufferedImage.SCALE_DEFAULT), bi.getType());
            }
        }

        writeImageFile(bi, dest.outfile);
    }

    public static class ImageDestinationDefinition {
        public String outfile = null;
        public int maxw = -1;
        public int maxh = -1;
        public int maxe = -1;
        public boolean verify = false;

        public void set() {}
    }

    public static String infile = null;

    public static void main(final String[] args)
        throws IOException {
        final List<ImageDestinationDefinition> dests = commandLine(args);
        if(dests == null || dests.size() == 0) {
            usage();
            return;
        }

        if(infile == null) {
            usage();
            return;
        }

        final BufferedImage image = readBufferedImageFromFile(infile);

        for(final ImageDestinationDefinition dest: dests) {
            transcode(image, dest);

            if(dest.verify) {
                final RenderedImage im = readBufferedImageFromFile(dest.outfile);
                final int width2 = im.getWidth();
                final int height2 = im.getHeight();

                if(dest.maxw != width2 || dest.maxh != height2 || dest.maxe != ((width2 > height2) ? width2 : height2))
                    throw new IOException("Verification failed!");
            }
        }
    }

    /**
     * Converts an {@link Image} to a {@link BufferedImage} image in a really hacky
     * way.
     */
    private static BufferedImage convert(final Image im, final int type) {
        if(im instanceof BufferedImage)
            return (BufferedImage)im;
        final BufferedImage bi = new BufferedImage(im.getWidth(null), im.getHeight(null), type);
        final Graphics bg = bi.getGraphics();
        bg.drawImage(im, 0, 0, null);
        bg.dispose();
        return bi;
    }

    private static CvMat doReadMatFromFile(final String filename, final boolean tryOther, final int mode) throws IOException {
        LOGGER.trace("OCV Reading CvMat from {}", filename);
        final File f = new File(filename);
        if(!f.exists())
            throw new FileNotFoundException(filename);

        try (final CvMat mat = CvMat.move(Imgcodecs.imread(filename, mode));) {
            if(tryOther && (mat == null || (mat.rows() == 0 && mat.cols() == 0))) {
                LOGGER.debug("OCV Failed to read '" + filename + "' using OpenCV");
                try {
                    return Utils.img2CvMat(doReadBufferedImageFromFile(filename, false, 0));
                } catch(final IllegalArgumentException iae) { //
                    return null;
                }
            } // else {
              // if(filename.endsWith(".jp2") && CvType.channels(mat.channels()) > 1)
              // Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
              // ret = CvMat.move(mat);
              // }
            if(mat != null) {
                LOGGER.trace("OCV Read {} from {}", mat, filename);
                return mat.returnMe();
            } else {
                LOGGER.debug("OCV Failed to read '" + filename + "' using OpenCV");
                return null;
            }
        }
    }

    private static class ReaderAndStream implements AutoCloseable {
        public final ImageReader reader;
        public final ImageInputStream stream;

        public ReaderAndStream(final ImageReader reader, final ImageInputStream stream) {
            this.reader = reader;
            this.stream = stream;
            reader.setInput(stream, true, true);
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    private static ReaderAndStream getNextReaderAndStream(final File f, final int index) throws IOException {
        final ImageInputStream input = ImageIO.createImageInputStream(f);

        final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        int cur = 0;
        while(readers.hasNext() && cur <= (index - 1)) {
            readers.next();
            cur++;
        }

        ImageReader reader = null;
        if(readers.hasNext())
            reader = readers.next();

        if(reader == null)
            input.close();

        return reader == null ? null : new ReaderAndStream(reader, input);
    }

    private static BufferedImage doReadBufferedImageFromFile(final String filename, final boolean tryOther, final int imageNumber) throws IOException {
        final File f = new File(filename);
        if(!f.exists())
            throw new FileNotFoundException(filename);

        Exception lastException = null;
        int cur = 1;
        while(true) {
            try (ReaderAndStream ras = getNextReaderAndStream(f, cur)) {
                if(ras != null) {
                    final ImageReader reader = ras.reader;
                    final ImageReadParam param = reader.getDefaultReadParam();
                    try {
                        System.out.println(reader);
                        LOGGER.trace("IIO attempt {}. Trying to read {} using reader {}", cur, filename, reader);
                        final BufferedImage image = reader.read(imageNumber, param);
                        return image;
                    } catch(final IndexOutOfBoundsException ioob) {
                        // TODO: distinguish between IndexOutOfBoundsException because imageNumber is
                        // too high
                        // and IndexOutOfBoundsException for some other reason.
                        if(imageNumber == 0) { // then this is certainly NOT because the imageNumber is too hight
                            LOGGER.debug("IIO attempt {} using reader {} failed with ", cur, reader, ioob);
                            lastException = ioob;
                        } else {
                            throw ioob; // for now, assume the reason this happened is because the imageNumber is too
                                        // hight
                                        // but there needs to be a better solution. Perhaps distinguish between
                                        // IndexOutOfBoundsException and ArrayIndexOutOfBoundsException for example
                        }
                    } catch(final IOException | RuntimeException ioe) {
                        LOGGER.debug("IIO attempt {} using reader {} failed with ", cur, reader, ioe);
                        lastException = ioe;
                    } finally {
                        reader.dispose();
                    }
                } else
                    break;
            }
            cur++;
        }

        if(cur == 0)
            LOGGER.debug("IIO No ImageIO reader's available for {}", filename);
        else
            LOGGER.debug("IIO No more ImageIO readers to try for {}", filename);

        LOGGER.info("IIO Failed to read '{}' using ImageIO", filename);
        if(!tryOther)
            throw new IllegalArgumentException("Can't read '" + filename + "' as an image. No codec worked in ImageIO");

        BufferedImage ret = null;
        try (final CvMat mat = doReadMatFromFile(filename, false, IMREAD_UNCHANGED);) {
            if(mat == null) {
                if(lastException != null)
                    throw new IllegalArgumentException("Can't read '" + filename + "' as an image. No codec worked in either ImageIO or OpenCv", lastException);
                else
                    throw new IllegalArgumentException("Can't read '" + filename + "' as an image. No codec worked in either ImageIO or OpenCv");
            }
            // if(filename.endsWith(".jp2") && CvType.channels(mat.channels()) > 1)
            // Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
            ret = Utils.mat2Img(mat);
        }
        LOGGER.trace("IIO Read {} from {}", ret, filename);
        return ret;
    }

    private static boolean doWrite(final BufferedImage ri, final String filename) throws IOException {
        LOGGER.trace("Writing image {} to {}", ri, filename);
        final int dotindex = filename.lastIndexOf(".");
        if(dotindex < 0)
            throw new IOException("No extention on " + filename);
        final String ext = filename.substring(dotindex + 1);

        final File f = new File(filename).getCanonicalFile();
        final File p = f.getParentFile();
        // make sure the output directory exists.
        p.mkdirs();
        final Iterator<ImageWriter> iter = ImageIO.getImageWritersBySuffix(ext);
        boolean wrote = false;
        while(iter.hasNext()) {
            final ImageWriter writer = iter.next(); // grab the first one
            try (final ImageOutputStream ios = ImageIO.createImageOutputStream(f);) {
                final ImageWriteParam param = writer.getDefaultWriteParam();

                writer.setOutput(ios);

                writer.write(null, new IIOImage(ri, null, null), param);
            }
            wrote = true;
        }
        return wrote;
    }

    private static boolean doWrite(final Mat ri, final String filename, final boolean canOverwrite) throws IOException {
        LOGGER.trace("Writing image {} to {}", ri, filename);
        try (final CvMat newMat = new CvMat();) {
            final Mat toWrite;
            if(filename.endsWith(".jp2")) {
                toWrite = (canOverwrite) ? ri : newMat;
                Imgproc.cvtColor(ri, toWrite, Imgproc.COLOR_BGR2RGB);
            } else
                toWrite = ri;

            return Imgcodecs.imwrite(filename, toWrite);
        }
    }

    private static double scale(final int width, final int height, final ImageDestinationDefinition dest) {
        double scale = -1.0;
        if(dest.maxh != -1) {
            if(height > dest.maxh)
                // see what we need to scale to make the height the same.
                scale = ((double)dest.maxh) / ((double)height);
        }

        if(dest.maxw != -1) {
            final int adjwidth = (scale >= 0.0) ? (int)Math.round(scale * width) : width;
            if(adjwidth > dest.maxw) {
                scale = ((double)dest.maxw) / ((double)adjwidth);
            }
        }

        if(dest.maxe != -1) {
            final int adjedge = width > height ? (scale >= 0.0 ? (int)Math.round(scale * width) : width)
                : (scale >= 0.0 ? (int)Math.round(scale * height) : height);
            if(adjedge > dest.maxe) {
                scale = ((double)(dest.maxe)) / ((double)adjedge);
            }
        }
        return scale;
    }

    private static List<ImageDestinationDefinition> commandLine(final String[] args) {
        final List<ImageDestinationDefinition> ret = new ArrayList<>();
        ImageDestinationDefinition cur = null;

        for(int i = 0; i < args.length; i++) {
            final String optionArg = args[i];
            // see if we are asking for help
            if("help".equalsIgnoreCase(optionArg) ||
                "-help".equalsIgnoreCase(optionArg)) {
                usage();
                return null;
            }

            if("-i".equalsIgnoreCase(optionArg)) {
                if(infile != null) {
                    System.err.println("One infile only");
                    usage();
                    return null;
                }
                infile = args[i + 1];
                i++;
            }

            else if("-o".equalsIgnoreCase(args[i])) {
                cur = cur == null ? new ImageDestinationDefinition() : cur;
                if(cur.outfile != null)
                    cur = push(cur, ret);
                cur.outfile = args[i + 1];
                i++;
            } else if("-verify".equalsIgnoreCase(args[i])) {
                cur = cur == null ? new ImageDestinationDefinition() : cur;
                if(cur.verify == false)
                    cur = push(cur, ret);
                cur.verify = true;
            } else if("-maxw".equalsIgnoreCase(args[i])) {
                cur = cur == null ? new ImageDestinationDefinition() : cur;
                if(cur.maxw != -1)
                    cur = push(cur, ret);
                cur.maxw = Integer.parseInt(args[i + 1]);
                i++;
            } else if("-maxh".equalsIgnoreCase(args[i])) {
                cur = cur == null ? new ImageDestinationDefinition() : cur;
                if(cur.maxh != -1)
                    cur = push(cur, ret);
                cur.maxh = Integer.parseInt(args[i + 1]);
                i++;
            } else if("-maxe".equalsIgnoreCase(args[i])) {
                cur = cur == null ? new ImageDestinationDefinition() : cur;
                if(cur.maxe != -1)
                    cur = push(cur, ret);
                cur.maxe = Integer.parseInt(args[i + 1]);
                i++;
            } else {
                usage();
                return null;
            }
        }

        if(cur != null) {
            cur.set();
            ret.add(cur);
        }

        return ret;
    }

    private static ImageDestinationDefinition push(final ImageDestinationDefinition cur,
        final List<ImageDestinationDefinition> ret) {
        ret.add(cur);
        cur.set();
        return new ImageDestinationDefinition();
    }

    private static void usage() {
        System.out.println("usage: java [javaargs] ImageFile -i infile -o outfile [-maxw width] [-maxh height] [-maxe maxEdge] [-verify]");
        System.out.println("       options -o through -verify can be repeated to convert an image file");
        System.out.println("       to a number of different formats and dimentions");
    }
}
