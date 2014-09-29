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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;
import org.sourcepit.common.utils.path.PathMatcher;

public class MainTest
{

   @Test
   public void test() throws IOException
   {
      String textile = "@hallo@";
      assertEquals("`hallo`", new Pandoc().toMarkdown(textile));
   }

   @Test
   public void testToRegex() throws Exception
   {
      StringBuilder sb = new StringBuilder();
      sb.append("/*\n");
      sb.append(" * Copyright dddd Sourcepit.org and others.\n");
      sb.append(" * \n");
      sb.append(" * Licensed under the Apache License, Version 2.0 (the \"License\");\n");
      sb.append(" * you may not use this file except in compliance with the License.\n");
      sb.append(" * You may obtain a copy of the License at\n");
      sb.append(" * \n");
      sb.append(" * http://www.apache.org/licenses/LICENSE-2.0\n");
      sb.append(" * \n");
      sb.append(" * Unless required by applicable law or agreed to in writing, software\n");
      sb.append(" * distributed under the License is distributed on an \"AS IS\" BASIS,\n");
      sb.append(" * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n");
      sb.append(" * See the License for the specific language governing permissions and\n");
      sb.append(" * limitations under the License.\n");
      sb.append(" */\n");

      System.out.println(PathMatcher.escRegEx(sb.toString()));
   }
}
