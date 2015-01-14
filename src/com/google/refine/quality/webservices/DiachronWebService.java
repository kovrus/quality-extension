package com.google.refine.quality.webservices;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.Jsonizable;
import com.google.refine.quality.problems.QualityProblem;
import com.google.refine.quality.utilities.JenaModelLoader;
import com.hp.hpl.jena.rdf.model.Model;

public class DiachronWebService {
  private static final int SC_BAD_REQUEST = 400;
  private static final int SC_OK = 200;
  private static final String SERIALIZATION = "Turtle";

  /**
   * Returns the cleaning quality report as a JSON entity in the http response.
   * See D3.2 section 4.2.4.
   * @throws IOException
   * @throws JSONException
   */
  public static void getCleaningSuggestions(HttpServletRequest request, HttpServletResponse response)
      throws IOException, JSONException {
    try {
      String datasetURL = getDatasetURL(request);
      List<String> metrics = getMetrics(request);
      Model model = JenaModelLoader.getModel(datasetURL);

      List<QualityProblem> problems = CleaningUtils.identifyQualityProblems(model, metrics);
      StringWriter out = new StringWriter();
      generateQualityReport(datasetURL, model.size(), problems).write(out, SERIALIZATION);

      response.setStatus(SC_OK);
      respondFile(response,"application/octet-stream", "cleaningSuggestions.ttl", out.toString().getBytes());
    } catch (IllegalArgumentException e) {
      response.setStatus(SC_BAD_REQUEST);
      respond(response, "error", "Request parameters are not complete.");
    } catch (Exception e) {
      response.setStatus(SC_BAD_REQUEST);
      respond(response, "error",
          "Request parameters cannot be parsed or an error in applying metrics");
    }
  }

  /**
   * Cleans a dataset. See D3.2 section 4.2.4.
   * @param request The request should have three parameters.
   *  "download" is a URI of the dataset to be cleaned.
   *  "metrics" is a json array of metrics to be applied for the cleaning.
   *  "delta" is flag defining whether cleaned statements must be returned.
   * @throws IOException
   * @throws JSONException
   */
  public static void clean(HttpServletRequest request, HttpServletResponse response)
      throws IOException, JSONException {
    try {
      String datasetURL = getDatasetURL(request);
      List<String> metrics = getMetrics(request);
      //final boolean delta = getDelta(request);

      Model model = JenaModelLoader.getModel(datasetURL);
      List<QualityProblem> problems = CleaningUtils.identifyQualityProblems(model, metrics);
      CleaningUtils.cleanModel(model, problems);

      StringWriter out = new StringWriter();
      model.write(out, SERIALIZATION);
      final Model deltaModel = CleaningUtils.getDeltaModel(model, problems);
      
      response.setStatus(SC_OK);
      ///Temporary Code TODO remove this hashtable. It uses always two keys,
      // they can be passed as params.
      Hashtable<String, StringWriter> outputEntries = new Hashtable<String, StringWriter>();
      outputEntries.put("cleanedModel.ttl", out);
      
      out = new StringWriter();
      deltaModel.write(out, SERIALIZATION);

      outputEntries.put("deltaModel.ttl", out);
      respondFile(response, "application/zip", "updatedModels.zip", getZippedBytes(outputEntries));
      ////////
      
      //Removed Temporarily 
      /*respondJSON(response, new Jsonizable() {
        @Override
        public void write(JSONWriter writer, Properties options) throws JSONException {
          writer.object();
          writer.key("status").value("ok");
          // TODO return just url where the model located. Tmp returning serialized model.
          writer.key("uri").value(out.toString());
          if (delta) {
            StringWriter out = new StringWriter();
            deltaModel.write(out, SERIALIZATION);
            writer.key("delta").value(out.toString());
          }
          writer.endObject();
        }
      }, new Properties());*/
    } catch (IllegalArgumentException e) {
      response.setStatus(SC_BAD_REQUEST);
      respond(response, "error", "Request parameters are not complete.");
    } catch (Exception e) {
      response.setStatus(SC_BAD_REQUEST);
      respond(response, "error",
        "Request parameters cannot be parsed or an error in applying metrics");
    }
  }

  /**
   * Parses a http request for a dataset URI.
   * @return A dataset URI as a string.
   * @throws IllegalArgumentException if the parameter is missing.
   */
  protected static String getDatasetURL(HttpServletRequest request) throws IllegalArgumentException {
    String dataset = request.getParameter("download");
    if (dataset == null) {
      throw new IllegalArgumentException();
    }
    return dataset;
  }

  /**
   * Parses a http request for the delta flag.
   * @return The delta flag as a boolean.
   * @throws IllegalArgumentException if the parameter is missing.
   */
  protected static boolean getDelta(HttpServletRequest request) throws IllegalArgumentException {
    String deltaParam = request.getParameter("delta");
    boolean delta = false;
    if (deltaParam == null) {
      throw new IllegalArgumentException();
    } else {
      delta = Boolean.parseBoolean(deltaParam);
    }
    return delta;
  }

  /**
   * Parses a metric parameter of the request. The string parameter has a format
   * of a json array. Example of a string ["metrics1", "metrics2"].
   * @return A list of metrics.
   * @throws IllegalArgumentException if the parameter is missing.
   * @throws JSONException if a parameter content can not be parsed as JSONArray.
   */
  protected static List<String> getMetrics(HttpServletRequest request)
      throws IllegalArgumentException, JSONException {
    String metrics = request.getParameter("metrics");
    if (metrics == null) {
      throw new IllegalArgumentException();
    }
    JSONArray metricsArray = new JSONArray(metrics);
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < metricsArray.length(); i++) {
      list.add(metricsArray.getString(i));
    }
    return list;
  }

  /**
   * Generates the quality report model.
   * @return A quality report RDF jena model.
   */
  protected static Model generateQualityReport(String dataset, long triples,
      List<QualityProblem> qualityProblems) {
    QualityReport qualityReport = new QualityReport(dataset);
    QualityStatistic qualityStatistic = new QualityStatistic(triples, qualityProblems.size());

    for (QualityProblem problem : qualityProblems) {
      qualityReport.addQualityProblem(problem);
      qualityStatistic.incrementProblemCounter(problem.getProblemURI());
    }
    qualityReport.setQualityStatistic(qualityStatistic.getQualityStatisticModel());
    return qualityReport.getQualityReportModel();
  }

  private static byte[] getZippedBytes(Hashtable<String, StringWriter> entries) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ZipOutputStream zos = new ZipOutputStream(baos);

    Iterator<Map.Entry<String, StringWriter>> it = entries.entrySet().iterator();

    while(it.hasNext()) {
      Map.Entry<String, StringWriter> entry = it.next();
      zos.putNextEntry(new ZipEntry(entry.getKey()));
      zos.write(entry.toString().getBytes());
      zos.closeEntry();
    }
    zos.flush();
    baos.flush();
    zos.close();
    baos.close();
    return baos.toByteArray();
  }

  private static void respondJSON(HttpServletResponse response, Jsonizable o, Properties options)
      throws IOException, JSONException {
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Content-Type", "application/json");
    response.setHeader("Cache-Control", "no-cache");

    Writer w = response.getWriter();
    JSONWriter writer = new JSONWriter(w);

    o.write(writer, options);
    w.flush();
    w.close();
  }

  private static void respondFile(HttpServletResponse response, String contentType,
      String fileName, byte[] responseBytes) throws IOException {
    response.setContentType(contentType);
    response.setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");
  
    ServletOutputStream oStream = response.getOutputStream();
    oStream.write(responseBytes);
    oStream.flush();
    oStream.close();
  }

  private static void respond(HttpServletResponse response, String status, String message)
      throws IOException, JSONException {
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Content-Type", "application/json");
    response.setHeader("Cache-Control", "no-cache");

    Writer w = response.getWriter();
    JSONWriter writer = new JSONWriter(w);
    writer.object();
    writer.key("status"); writer.value(status);
    writer.key("message"); writer.value(message);
    writer.endObject();
    w.flush();
    w.close();
  }
}