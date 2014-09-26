/*
 * Copyright (c) 2014 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package de.sourcepit.redmine2github;

import static org.sourcepit.common.utils.lang.Exceptions.pipe;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.sourcepit.common.utils.file.FileUtils;
import org.sourcepit.common.utils.file.FileVisitor;
import org.sourcepit.common.utils.io.IO;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class RemoveHeaders implements FileVisitor
{
   @Option(name = "-d", required = true)
   private File dir;

   @Option(name = "-c", required = true)
   private String charset;

   public static void main(String[] args) throws CmdLineException
   {
      RemoveHeaders removeHeaders = new RemoveHeaders();

      CmdLineParser parser = new CmdLineParser(removeHeaders);
      parser.parseArgument(args);

      removeHeaders.run();
   }

   private void run()
   {
      FileUtils.accept(dir, this);
   }

   @Override
   public boolean visit(File file)
   {
      final String name = file.getName();
      if (file.isFile())
      {
         try
         {
            if (name.endsWith(".java") || name.endsWith(".aj"))
            {
               processJavaFile(file);
            }
            else if (name.endsWith(".xml"))
            {
               processXmlFile(file);
            }
         }
         catch (IOException e)
         {
            throw pipe(e);
         }
      }

      return !name.startsWith(".") && !"target".equals(name);
   }

   private static final String nl = System.getProperty("line.separator");

   private void processXmlFile(File file) throws IOException
   {
      String content = read(file);
      if (!content.startsWith("<?"))
      {
         System.err.println(file);
         content = "<?xml version=\"1.0\" encoding=\"" + charset + "\"?>" + nl + content;
         write(file, content);
      }
   }

   private void processJavaFile(File file) throws IOException
   {
      System.out.println(file);

      String content = read(file);

      int idx = content.indexOf("package ");
      if (idx < 0)
      {
         System.err.println(file);
      }
      else
      {
         content = content.substring(idx, content.length());
         write(file, content);
      }
   }

   private void write(File file, String content) throws FileNotFoundException, IOException
   {
      OutputStream out = null;
      try
      {
         out = new BufferedOutputStream(new FileOutputStream(file));
         IOUtils.write(content, out, charset);
      }
      finally
      {
         IOUtils.closeQuietly(out);
      }
   }

   private String read(File file) throws FileNotFoundException, IOException
   {
      InputStream in = null;
      try
      {
         in = new FileInputStream(file);
         return IOUtils.toString(new BufferedInputStream(in), charset);
      }
      finally
      {
         IOUtils.closeQuietly(in);
      }
   }
}
