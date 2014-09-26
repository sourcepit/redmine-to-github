/*
 * Copyright (c) 2014 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package de.sourcepit.redmine2github;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class Pandoc
{

   public String toMarkdown(String textile) throws IOException
   {
      File in = File.createTempFile("foo", ".textile");
      in.deleteOnExit();
      write(textile, in);

      File out = File.createTempFile("foo", ".markdown");
      out.deleteOnExit();

      try
      {

         Process process = Runtime.getRuntime().exec(
            MessageFormat.format(
               "C:/Users/imm0136/AppData/Local/Pandoc/pandoc.exe -f textile -t  markdown_github {0} -o {1}", in, out));

         final int exit;
         try
         {
            exit = process.waitFor();
         }
         catch (InterruptedException e)
         {
            throw new IllegalStateException();
         }

         if (exit != 0)
         {
            throw new IllegalStateException();
         }

         return read(out).trim();
      }
      finally
      {
         FileUtils.deleteQuietly(in);
         FileUtils.deleteQuietly(out);
      }
   }

   private void write(String content, File in) throws IOException
   {
      FileOutputStream out = null;
      try
      {
         out = new FileOutputStream(in);
         IOUtils.write(content, out);
      }
      finally
      {
         IOUtils.closeQuietly(out);
      }
   }

   private String read(File file) throws IOException
   {
      FileInputStream in = null;
      try
      {
         in = new FileInputStream(file);
         return IOUtils.toString(in);
      }
      finally
      {
         IOUtils.closeQuietly(in);
      }
   }
}
