/*
 * Copyright 2014 Bernd Vogt and others.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
