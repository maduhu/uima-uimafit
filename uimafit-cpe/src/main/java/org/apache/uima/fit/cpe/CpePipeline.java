/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.uima.fit.cpe;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.EntityProcessStatus;
import org.apache.uima.collection.StatusCallbackListener;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.xml.sax.SAXException;

/**
 */
public final class CpePipeline {
  private CpePipeline() {
    // No instances
  }
  
  /**
   * Run the CollectionReader and AnalysisEngines as a multi-threaded pipeline.
   * 
   * @param readerDesc
   *          The CollectionReader that loads the documents into the CAS.
   * @param descs
   *          Primitive AnalysisEngineDescriptions that process the CAS, in order. If you have a mix
   *          of primitive and aggregate engines, then please create the AnalysisEngines yourself
   *          and call the other runPipeline method.
   * @throws SAXException
   *           if there was a XML-related problem materializing the component descriptors that are
   *           referenced from the CPE descriptor
   * @throws IOException
   *           if there was a I/O-related problem materializing the component descriptors that are
   *           referenced from the CPE descriptor
   * @throws CpeDescriptorException
   *           if there was a problem configuring the CPE descriptor
   * @throws UIMAException
   *           if there was a problem initializing or running the CPE.
   */
  public static void runPipeline(final CollectionReaderDescription readerDesc,
          final AnalysisEngineDescription... descs) throws UIMAException, SAXException,
          CpeDescriptorException, IOException {
    // Create AAE
    final AnalysisEngineDescription aaeDesc = createEngineDescription(descs);

    CpeBuilder builder = new CpeBuilder();
    builder.setReader(readerDesc);
    builder.setAnalysisEngine(aaeDesc);
    builder.setMaxProcessingUnitThreadCount(Runtime.getRuntime().availableProcessors() - 1);

    StatusCallbackListenerImpl status = new StatusCallbackListenerImpl();
    CollectionProcessingEngine engine = builder.createCpe(status);

    engine.process();
    try {
      synchronized (status) {
        while (status.isProcessing) {
          status.wait();
        }
      }
    } catch (InterruptedException e) {
      // Do nothing
    }

    if (status.exceptions.size() > 0) {
      throw new AnalysisEngineProcessException(status.exceptions.get(0));
    }
  }

  private static class StatusCallbackListenerImpl implements StatusCallbackListener {

    private final List<Exception> exceptions = new ArrayList<Exception>();

    private boolean isProcessing = true;

    public void entityProcessComplete(CAS arg0, EntityProcessStatus arg1) {
      if (arg1.isException()) {
        for (Exception e : arg1.getExceptions()) {
          exceptions.add(e);
        }
      }
    }

    public void aborted() {
      synchronized (this) {
        if (isProcessing) {
          isProcessing = false;
          notify();
        }
      }
    }

    public void batchProcessComplete() {
      // Do nothing
    }

    public void collectionProcessComplete() {
      synchronized (this) {
        if (isProcessing) {
          isProcessing = false;
          notify();
        }
      }
    }

    public void initializationComplete() {
      // Do nothing
    }

    public void paused() {
      // Do nothing
    }

    public void resumed() {
      // Do nothing
    }
  }
}
