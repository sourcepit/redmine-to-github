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

import static org.sourcepit.common.utils.lang.Exceptions.pipe;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.sourcepit.common.utils.file.FileUtils;
import org.sourcepit.common.utils.file.FileVisitor;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class RemoveHeaders implements FileVisitor
{
   @Option(name = "-d", required = true)
   private File dir;

   @Option(name = "-c", required = true)
   private String charset;

   private static final String RAW_HEADER;

   private static final String JAVA_HEADER;

   private static final String XML_HEADER;

   static
   {
      StringBuilder sb = new StringBuilder();

      sb.append("Copyright 2014 Bernd Vogt and others.\n");
      sb.append("\n");
      sb.append("Licensed under the Apache License, Version 2.0 (the \"License\");\n");
      sb.append("you may not use this file except in compliance with the License.\n");
      sb.append("You may obtain a copy of the License at\n");
      sb.append("\n");
      sb.append("   http://www.apache.org/licenses/LICENSE-2.0\n");
      sb.append("\n");
      sb.append("Unless required by applicable law or agreed to in writing, software\n");
      sb.append("distributed under the License is distributed on an \"AS IS\" BASIS,\n");
      sb.append("WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n");
      sb.append("See the License for the specific language governing permissions and\n");
      sb.append("limitations under the License.\n");

      RAW_HEADER = sb.toString();

      JAVA_HEADER = "/*\n" + RAW_HEADER + "*/\n\n";

      XML_HEADER = "<!--\n" + RAW_HEADER + "-->\n";
   }

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
      System.out.println(file);

      String content = read(file);
      if (!content.startsWith("<?"))
      {
         System.err.println(file);
         content = "<?xml version=\"1.0\" encoding=\"" + charset + "\"?>" + nl + XML_HEADER + content;
         write(file, content);
      }
      else
      {
         int firstNL = content.indexOf('\n');
         if (firstNL > -1)
         {
            int idx = content.indexOf('<', firstNL + 2);
            content = "<?xml version=\"1.0\" encoding=\"" + charset + "\"?>" + nl + XML_HEADER
               + content.substring(idx, content.length());
            write(file, content);
         }
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
         content = JAVA_HEADER + content.substring(idx, content.length());
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
