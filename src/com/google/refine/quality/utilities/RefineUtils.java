package com.google.refine.quality.utilities;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.refine.commands.Command;
import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Project;
import com.google.refine.operations.cell.TextTransformOperation;
import com.google.refine.operations.column.ColumnAdditionOperation;
import com.google.refine.operations.column.ColumnRemovalOperation;
import com.google.refine.operations.column.ColumnRenameOperation;
import com.google.refine.operations.column.ColumnSplitOperation;
import com.google.refine.process.Process;

public class RefineUtils extends Command {
  public static void splitColumn(Project project, HttpServletRequest request,
      HttpServletResponse response, String columnName, int columns) throws IOException, ServletException {
    try {
      JSONObject engineConfig = new JSONObject();
      engineConfig.put("facets", new JSONArray());
      engineConfig.put("mode", "separator");

      AbstractOperation op = new ColumnSplitOperation(engineConfig, columnName, false, true,
          Constants.COLUMN_SPLITER, false, columns);
      Process process = op.createProcess(project, new Properties());

      performProcessAndRespond(request, response, project, process);
    } catch (Exception e) {
      respondException(response, e);
    }
  }

  public static void renameColumn(Project project, HttpServletRequest request,
      HttpServletResponse response, String newColumnName, String oldColumnName) throws IOException,
      ServletException {
    try {
      AbstractOperation op = new ColumnRenameOperation(oldColumnName, newColumnName);
      Process process = op.createProcess(project, new Properties());
      performProcessAndRespond(request, response, project, process);
    } catch (Exception e) {
      e.printStackTrace();
      respondException(response, e);
    }
  }

  public static void addColumn(Project project, HttpServletRequest request,
      HttpServletResponse response, String newColumnName, String baseColumnName,
      int columnInsertIndex) throws IOException, ServletException {
    try {
      String expression = "value.replace(/(?s).*/, \"\")";
      String onError = "set-to-blank";
      JSONObject engineConfig = new JSONObject();
      engineConfig.put("facets", new JSONArray());
      engineConfig.put("mode", "row-based");

      AbstractOperation op = new ColumnAdditionOperation(engineConfig, baseColumnName, expression,
          TextTransformOperation.stringToOnError(onError), newColumnName, columnInsertIndex);

      Process process = op.createProcess(project, new Properties());

      performProcessAndRespond(request, response, project, process);
    } catch (Exception e) {
      e.printStackTrace();
      respondException(response, e);
    }
  }

  public static void removeColumn(Project project, HttpServletRequest request,
      HttpServletResponse response, String columnName) throws IOException, ServletException {
    try {
      AbstractOperation op = new ColumnRemovalOperation(columnName);
      Process process = op.createProcess(project, new Properties());

      performProcessAndRespond(request, response, project, process);
    } catch (Exception e) {
      respondException(response, e);
    }
  }
}