package com.google.refine.quality.problems;

import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.sparql.core.Quad;

public class UndefinedPropertyProblem extends QualityProblem {

  public UndefinedPropertyProblem(Quad quad, Resource qualityReport) {
    super(quad, qualityReport);
  }

  @Override
  public String getProblemDescription() {
    return String.format("%s is not defined", quad.getPredicate());
  }
}