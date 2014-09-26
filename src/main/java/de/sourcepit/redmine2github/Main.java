/*
 * Copyright (c) 2014 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package de.sourcepit.redmine2github;

import static java.lang.Integer.valueOf;
import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public final class Main
{
   @Option(name = "-r", aliases = "--redmine-url", required = true)
   private URL redmineURL;

   @Option(name = "-go", aliases = "--github-owner", required = true)
   private String githubOwner;

   @Option(name = "-gr", aliases = "--github-repo", required = true)
   private String githubRepo;

   public static void main(String[] args) throws CmdLineException, IOException
   {
      final Main main = new Main();

      CmdLineParser parser = new CmdLineParser(main);
      parser.parseArgument(args);

      main.go();
   }

   private Main()
   {
      super();
   }

   void go() throws IOException
   {
      final URL githubURL = new URL("https://api.github.com");

      final HttpClientBuilder clientBuilder = HttpClients.custom();
      final HttpHost proxy = determineProxy();
      if (proxy != null)
      {
         clientBuilder.setProxy(proxy);
      }

      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

      credentialsProvider.setCredentials(new AuthScope(redmineURL.getHost(), AuthScope.ANY_PORT),
         new UsernamePasswordCredentials("xxx", "xxx"));

      credentialsProvider.setCredentials(new AuthScope(githubURL.getHost(), AuthScope.ANY_PORT),
         new UsernamePasswordCredentials("xxx", "xxx"));

      clientBuilder.setDefaultCredentialsProvider(credentialsProvider);

      // RequestConfig.custom().setAuthenticationEnabled(authenticationEnabled)
      // clientBuilder.setDefaultRequestConfig(config)
      final CloseableHttpClient httpClient = clientBuilder.build();


      HttpHost redmine = new HttpHost(redmineURL.getHost(), redmineURL.getPort(), redmineURL.getProtocol());
      HttpHost github = new HttpHost(githubURL.getHost(), githubURL.getPort(), githubURL.getProtocol());

      AuthCache authCache = new BasicAuthCache();
      authCache.put(github, new BasicScheme());

      // Add AuthCache to the execution context
      final HttpClientContext context = HttpClientContext.create();
      context.setCredentialsProvider(credentialsProvider);
      context.setAuthCache(authCache);

      try
      {

         int projectId = 20;

         Map<Integer, String> users = new HashMap<Integer, String>();
         users.put(Integer.valueOf(3), "berndv");
         users.put(Integer.valueOf(51), "u3r");

         // Map<Integer, Integer> versionMapping = new HashMap<Integer, Integer>();
         Map<Integer, Integer> versionMapping = migrateVersions(httpClient, redmine, github, context, projectId);

         final List<JsonObject> issues = requestIssues(httpClient, redmine, projectId);
         Collections.sort(issues, new Comparator<JsonObject>()
         {
            @Override
            public int compare(JsonObject o1, JsonObject o2)
            {
               int id1 = o1.getAsJsonPrimitive("id").getAsInt();
               int id2 = o2.getAsJsonPrimitive("id").getAsInt();
               return id1 - id2;
            }
         });

         labels(issues, httpClient, github, context);

         final Map<Integer, Integer> issueMappings = issues(httpClient, github, context, users, versionMapping, issues);

         for (Integer redIssueId : issueMappings.keySet())
         {
            for (JsonObject journal : requestJournals(httpClient, redmine, redIssueId.intValue()))
            {
               final JsonPrimitive notes = journal.getAsJsonObject().getAsJsonPrimitive("notes");
               if (notes != null)
               {
                  final String markdown = toMarkdown(notes.getAsString());
                  if (!markdown.isEmpty())
                  {
                     createComment(httpClient, github, context, issueMappings.get(redIssueId).intValue(), markdown);
                  }
               }
            }
         }

      }
      finally
      {
         closeQuietly(httpClient);
      }
   }

   private Map<Integer, Integer> issues(final CloseableHttpClient httpClient, HttpHost github,
      final HttpClientContext context, Map<Integer, String> users, Map<Integer, Integer> versionMapping,
      final List<JsonObject> issues) throws IOException, ClientProtocolException
   {
      int current = 1;

      boolean dummyMarker = false;

      final Map<Integer, Integer> issuesMapping = new HashMap<Integer, Integer>();

      for (JsonObject issue : issues)
      {
         // if (current > 6)
         // {
         // break;
         // }

         final int id = issue.getAsJsonPrimitive("id").getAsInt();

         while (current < id)
         {
            if (dummyMarker)
            {
               createLabel(httpClient, github, context, "Dummies");
               dummyMarker = false;
            }

            JsonObject dummy = new JsonObject();
            dummy.addProperty("title", "Dummy to keep Redmine and Github issue numbers in sync (" + current + ")");
            dummy
               .addProperty(
                  "body",
                  "This issue was generated during the project migration from Redmine to GitHub. Its only a dummy to keep issue numbers of both systems in sync.");


            dummy.addProperty("state", "Dummy to keep Redmine and Github issue numbers in sync (" + current + ")");

            dummy = createIssue(httpClient, github, context, dummy);


            int nr = dummy.getAsJsonPrimitive("number").getAsInt();
            close(httpClient, github, context, nr);

            if (nr > current && nr < id)
            {
               current = nr;
            }
            else if (nr != current)
            {
               throw new IllegalStateException();
            }

            current++;
         }

         current++;

         JsonObject gi = new JsonObject();

         gi.addProperty("title", issue.getAsJsonPrimitive("subject").getAsString());
         gi.addProperty("body", toMarkdown(issue.getAsJsonPrimitive("description").getAsString()));

         JsonObject version = issue.getAsJsonObject("fixed_version");
         if (version != null)
         {
            final int redmineVersionId = version.getAsJsonPrimitive("id").getAsInt();

            Integer milestone = versionMapping.get(Integer.valueOf(redmineVersionId));
            if (milestone != null)
            {
               gi.addProperty("milestone", milestone.intValue());
            }
         }

         JsonObject assignedTo = issue.getAsJsonObject("assigned_to");
         if (assignedTo != null)
         {
            String assignee = users.get(Integer.valueOf(assignedTo.getAsJsonPrimitive("id").getAsInt()));
            gi.addProperty("assignee", assignee);
         }

         JsonObject tracker = issue.getAsJsonObject("tracker");
         String label = tracker.getAsJsonPrimitive("name").getAsString();

         JsonArray labels = new JsonArray();
         labels.add(new JsonPrimitive(label));
         gi.add("labels", labels);

         gi = createIssue(httpClient, github, context, gi);

         final int gitIssueNr = gi.getAsJsonPrimitive("number").getAsInt();

         if ("Closed".equals(issue.getAsJsonObject("status").getAsJsonPrimitive("name").getAsString()))
         {
            close(httpClient, github, context, gitIssueNr);
         }

         System.out.println(MessageFormat.format("Issue {0}: {1}", gitIssueNr, gi.getAsJsonPrimitive("title")
            .getAsString()));

         issuesMapping.put(Integer.valueOf(id), gitIssueNr);
      }

      return issuesMapping;
   }

   private void createComment(CloseableHttpClient httpClient, HttpHost github, HttpClientContext context,
      int gitIssueNr, String comment) throws IOException
   {
      // /repos/:owner/:repo/issues/:number/comments

      JsonObject l = new JsonObject();
      l.addProperty("body", comment);

      HttpPost post = new HttpPost(MessageFormat.format("/repos/{0}/{1}/issues/{2}/comments", githubOwner, githubRepo,
         gitIssueNr));
      post.setEntity(new StringEntity(l.toString(), ContentType.APPLICATION_JSON));

      CloseableHttpResponse response = httpClient.execute(github, post, context);
      try
      {
         final StatusLine status = response.getStatusLine();
         if (status.getStatusCode() != 201)
         {
            throw new IllegalStateException(status.toString());
         }
      }
      finally
      {
         closeQuietly(response);
      }
   }

   private String toMarkdown(String textile)
   {
      try
      {
         return new Pandoc().toMarkdown(textile);
      }
      catch (IOException e)
      {
         System.err.println("Failed to convert : " + textile);
         e.printStackTrace();
         
         return textile;
      }
   }

   private void labels(final List<JsonObject> requestIssues, final CloseableHttpClient httpClient, HttpHost github,
      final HttpClientContext context) throws IOException, ClientProtocolException
   {
      final List<String> labels = extractLabels(requestIssues);
      for (String label : labels)
      {
         createLabel(httpClient, github, context, label);
      }
   }

   private void createLabel(final CloseableHttpClient httpClient, HttpHost github, final HttpClientContext context,
      String label) throws IOException, ClientProtocolException
   {
      JsonObject l = new JsonObject();
      l.addProperty("name", label);
      l.addProperty("color", "FFFFFF");

      HttpPost post = new HttpPost(MessageFormat.format("/repos/{0}/{1}/labels", githubOwner, githubRepo));
      post.setEntity(new StringEntity(l.toString(), ContentType.APPLICATION_JSON));

      CloseableHttpResponse response = httpClient.execute(github, post, context);
      try
      {
         final StatusLine status = response.getStatusLine();
         if (status.getStatusCode() != 201)
         {
            throw new IllegalStateException(status.toString());
         }
      }
      finally
      {
         closeQuietly(response);
      }
   }

   private void close(final CloseableHttpClient httpClient, HttpHost github, final HttpClientContext context,
      int issueNr) throws IOException, ClientProtocolException
   {
      JsonObject l = new JsonObject();
      l.addProperty("state", "closed");

      HttpPatch patch = new HttpPatch(MessageFormat.format("/repos/{0}/{1}/issues/{2}", githubOwner, githubRepo,
         issueNr));
      patch.setEntity(new StringEntity(l.toString(), ContentType.APPLICATION_JSON));

      CloseableHttpResponse response = httpClient.execute(github, patch, context);
      try
      {
         final StatusLine status = response.getStatusLine();
         if (status.getStatusCode() != 200)
         {
            throw new IllegalStateException(status.toString());
         }
      }
      finally
      {
         closeQuietly(response);
      }
   }

   private JsonObject createIssue(final CloseableHttpClient httpClient, HttpHost github,
      final HttpClientContext context, JsonObject issue) throws IOException, ClientProtocolException
   {
      HttpPost post = new HttpPost(MessageFormat.format("/repos/{0}/{1}/issues", githubOwner, githubRepo));
      post.setEntity(new StringEntity(issue.toString(), ContentType.APPLICATION_JSON));

      CloseableHttpResponse response = httpClient.execute(github, post, context);
      try
      {
         final StatusLine status = response.getStatusLine();
         if (status.getStatusCode() == 201)
         {
            return toJsonObject(response.getEntity());
         }
         else
         {
            throw new IllegalStateException(status.toString() + "\n"
               + IOUtils.toString(response.getEntity().getContent()));
         }
      }
      finally
      {
         closeQuietly(response);
      }
   }


   private List<String> extractLabels(List<JsonObject> requestIssues)
   {
      List<String> labels = new ArrayList<String>();
      for (JsonObject issue : requestIssues)
      {
         JsonObject tracker = issue.getAsJsonObject("tracker");
         String label = tracker.getAsJsonPrimitive("name").getAsString();
         if (!labels.contains(label))
         {
            labels.add(label);
         }
      }
      Collections.sort(labels);
      return labels;
   }

   private Map<Integer, Integer> migrateVersions(final CloseableHttpClient httpClient, HttpHost redmine,
      HttpHost github, final HttpClientContext context, int projectId) throws IOException, ClientProtocolException
   {
      Map<Integer, Integer> versionMapping = new HashMap<Integer, Integer>();

      final List<JsonObject> versions = requestVersions(httpClient, redmine, projectId);
      Collections.sort(versions, new Comparator<JsonObject>()
      {
         @Override
         public int compare(JsonObject o1, JsonObject o2)
         {
            String c1 = o1.getAsJsonPrimitive("created_on").getAsString();
            String c2 = o2.getAsJsonPrimitive("created_on").getAsString();
            return c1.compareTo(c2);
         }
      });

      for (JsonElement elem : versions)
      {
         JsonObject rm = elem.getAsJsonObject();

         final int id = rm.getAsJsonPrimitive("id").getAsInt();

         JsonObject gm = new JsonObject();
         gm.addProperty("title", rm.getAsJsonPrimitive("name").getAsString());
         gm.addProperty("state", rm.getAsJsonPrimitive("status").getAsString());
         gm.addProperty("description", rm.getAsJsonPrimitive("description").getAsString());

         HttpPost post = new HttpPost(MessageFormat.format("/repos/{0}/{1}/milestones", githubOwner, githubRepo));
         post.setEntity(new StringEntity(gm.toString(), ContentType.APPLICATION_JSON));

         CloseableHttpResponse response = httpClient.execute(github, post, context);
         try
         {
            final StatusLine status = response.getStatusLine();
            if (status.getStatusCode() == 201)
            {
               JsonObject obj = toJsonObject(response.getEntity());
               int number = obj.getAsJsonPrimitive("number").getAsInt();
               versionMapping.put(Integer.valueOf(id), Integer.valueOf(number));
            }
            else
            {
               throw new IllegalStateException(status.toString());
            }
         }
         finally
         {
            closeQuietly(response);
         }
      }
      return versionMapping;
   }

   private static List<JsonObject> requestIssues(final CloseableHttpClient httpClient, HttpHost redmine, int projectId)
      throws IOException
   {
      List<JsonObject> array = new ArrayList<JsonObject>();

      int limit = 25;
      int totalCound = Integer.MAX_VALUE;
      int offset = 0;

      while (offset < totalCound)
      {
         HttpGet get = issues(projectId, offset, limit);

         CloseableHttpResponse response = httpClient.execute(redmine, get);
         try
         {
            JsonObject obj = toJsonObject(response.getEntity());

            for (JsonElement jsonElement : obj.getAsJsonArray("issues"))
            {
               array.add(jsonElement.getAsJsonObject());
            }

            totalCound = obj.get("total_count").getAsInt();

            limit = obj.get("limit").getAsInt();

            offset = obj.get("offset").getAsInt() + limit;
         }
         finally
         {
            closeQuietly(response);
         }
      }

      return array;
   }

   private static List<JsonObject> requestVersions(final CloseableHttpClient httpClient, HttpHost redmine, int projectId)
      throws IOException
   {
      List<JsonObject> versions = new ArrayList<JsonObject>();
      HttpGet get = versions(projectId);
      CloseableHttpResponse response = httpClient.execute(redmine, get);
      try
      {
         for (JsonElement jsonElement : toJsonObject(response.getEntity()).getAsJsonArray("versions"))
         {
            versions.add(jsonElement.getAsJsonObject());
         }
         return versions;

      }
      finally
      {
         closeQuietly(response);
      }
   }

   private static HttpGet issues(int projectId, int offset, int limit)
   {
      return new HttpGet(MessageFormat.format(
         "/issues.json?project_id={0}&offset={1}&limit={2}&status_id={3}&include=journals", projectId, offset, limit,
         "*"));
   }

   private List<JsonObject> requestJournals(final CloseableHttpClient httpClient, HttpHost redmine, int issueId)
      throws IOException
   {
      List<JsonObject> journals = new ArrayList<JsonObject>();

      HttpGet get = new HttpGet(MessageFormat.format("/issues/{0}.json?include=journals", issueId));
      CloseableHttpResponse response = httpClient.execute(redmine, get);
      try
      {
         JsonObject issue = toJsonObject(response.getEntity()).getAsJsonObject("issue");

         JsonArray j = issue.getAsJsonArray("journals");
         if (j != null)
         {
            for (JsonElement jsonElement : j)
            {
               journals.add(jsonElement.getAsJsonObject());
            }
         }
         return journals;

      }
      finally
      {
         closeQuietly(response);
      }
   }

   private static HttpGet versions(int projectId)
   {
      return new HttpGet(MessageFormat.format("/projects/{0}/versions.json", projectId));
   }

   private static JsonObject toJsonObject(final HttpEntity entity) throws IOException
   {
      final ContentType contentType = ContentType.getOrDefault(entity);

      final InputStream in = entity.getContent();
      final Charset charset = contentType.getCharset();

      JsonObject jobj = (JsonObject) new JsonParser().parse(new InputStreamReader(in, charset));
      return jobj;
   }

   private static HttpHost determineProxy()
   {
      String proxyHost = System.getProperty("http.proxyHost");
      if (proxyHost != null)
      {
         String proxyPort = System.getProperty("http.proxyPort");
         if (proxyPort != null)
         {
            return new HttpHost(proxyHost, valueOf(proxyPort));
         }
      }
      return null;
   }

}
