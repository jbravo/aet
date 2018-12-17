/**
 * AET
 *
 * Copyright (C) 2013 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.cognifide.aet.runner.processing.steps;

import com.cognifide.aet.communication.api.JobStatus;
import com.cognifide.aet.communication.api.ProcessingError;
import com.cognifide.aet.communication.api.job.CollectorResultData;
import com.cognifide.aet.communication.api.job.ComparatorJobData;
import com.cognifide.aet.communication.api.metadata.CollectorStepResult;
import com.cognifide.aet.communication.api.metadata.Step;
import com.cognifide.aet.communication.api.metadata.Test;
import com.cognifide.aet.communication.api.metadata.Url;
import com.cognifide.aet.communication.api.queues.JmsConnection;
import com.cognifide.aet.communication.api.util.ExecutionTimer;
import com.cognifide.aet.runner.MessagesManager;
import com.cognifide.aet.runner.RunnerConfiguration;
import com.cognifide.aet.runner.processing.TimeoutWatch;
import com.cognifide.aet.runner.processing.data.wrappers.RunIndexWrapper;
import com.cognifide.aet.runner.scheduler.CollectorJobSchedulerService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CollectionResultsRouter - collects work from collector-workers, divides and schedules compare
 * work among compare-workers
 */
public class CollectionResultsRouter extends StepManager implements TaskFinishPoint {

  private static final Logger LOGGER = LoggerFactory.getLogger(CollectionResultsRouter.class);

  private static final String STEP_NAME = "COLLECTED";

  private final List<ChangeObserver> changeListeners;

  private final CollectorJobSchedulerService collectorJobScheduler;

  private RunIndexWrapper runIndexWrapper;

  private final ExecutionTimer timer;

  public CollectionResultsRouter(TimeoutWatch timeoutWatch, JmsConnection jmsConnection,
      RunnerConfiguration runnerConfiguration,
      CollectorJobSchedulerService collectorJobScheduler, RunIndexWrapper runIndexWrapper)
      throws JMSException {
    super(timeoutWatch, jmsConnection, runIndexWrapper.get().getCorrelationId(),
        runnerConfiguration.getMttl());
    this.collectorJobScheduler = collectorJobScheduler;
    this.runIndexWrapper = runIndexWrapper;
    this.messagesToReceive.getAndSet(runIndexWrapper.countUrls());
    this.changeListeners = new CopyOnWriteArrayList<>();
    timer = ExecutionTimer.createAndRun("collection");
  }

  @Override
  public void onMessage(Message message) {
    if (message instanceof ObjectMessage) {
      timeoutWatch.update();
      try {
        CollectorResultData collectorResultData = (CollectorResultData) ((ObjectMessage) message)
            .getObject();

        collectorJobScheduler.messageReceived(collectorResultData.getRequestMessageId(),
            message.getJMSCorrelationID());
        String testName = collectorResultData.getTestName();

        updateCounters(collectorResultData.getStatus());

        LOGGER.info(
            "Received message {} - url {} in test `{}` collected with status {}. Results received "
                + "successful {} / failed {} of {} total. CorrelationId: {}.",
            message.getJMSMessageID(), collectorResultData.getUrl(), testName,
            collectorResultData.getStatus(), messagesReceivedSuccess.get(),
            messagesReceivedFailed.get(),
            getTotalTasksCount(), correlationId);

        if (collectorResultData.getStatus() == JobStatus.SUCCESS) {
          onSuccess(collectorResultData, testName, correlationId);
        } else {
          final Url failedUrl = collectorResultData.getUrl();
          failedUrl.setErrorMessage(collectorResultData.getProcessingError().getDescription());
          updateSuiteUrl(testName, failedUrl);
          onError(collectorResultData.getProcessingError());
        }

        if (isFinished()) {
          LOGGER.info("CollectionResultsRouter task finished.");
          finishTask();
        }
      } catch (JMSException e) {
        LOGGER.error("Error while collecting results in CollectionResultsRouter", e);
        onError(ProcessingError.collectingError(e.getMessage()));
      }
    }
  }

  private void finishTask() throws JMSException {
    for (ChangeObserver changeListener : changeListeners) {
      changeListener.informChangesCompleted();
    }
    timer.finishAndLog(runIndexWrapper.get().getName());
    LOGGER.debug("Closing consumer!");
    consumer.close();
  }

  private void onSuccess(CollectorResultData collectorResultData, String testName,
      String patternSuiteCorrelationId) throws JMSException {
    final Url processedUrl = collectorResultData.getUrl();
    updateSuiteUrl(collectorResultData.getTestName(), processedUrl);
    for (Step step : processedUrl.getSteps()) {
      if (step.getStepResult().getStatus() == CollectorStepResult.Status.PROCESSING_ERROR) {
        LOGGER.error("Step {} finished with errors: {}!", step, step.getStepResult());
        onError(ProcessingError.collectingError(step.getStepResult().getErrors().toString()));
      } else if (hasComparators(step)) {
        if (step.getPatterns().isEmpty()) {
          step.addPattern(step.getStepResult().getArtifactId(), patternSuiteCorrelationId);
        }
        int scheduledMessagesNo = dispatch(step, testName, processedUrl.getName());
        LOGGER
            .info("{} ComparatorJobData messages send to queue {}. CorrelationId: {} TestName: {}",
                scheduledMessagesNo, getQueueOutName(), this.correlationId, testName);
        for (ChangeObserver changeListener : changeListeners) {
          changeListener.updateAmountToReceive(scheduledMessagesNo);
        }
      }
    }
  }

  private int dispatch(Step step, String testName, String urlName) throws JMSException {
    final int comparisonsNo = step.getComparators().size();
    LOGGER.debug(
        "Sending comparatorStep message. CorrelationID: {} TestName: {} UrlName: {} with {} comparators defined.",
        correlationId, testName, urlName, comparisonsNo);
    createAndSendComparatorJobData(step, testName, urlName);

    return comparisonsNo;
  }

  private void createAndSendComparatorJobData(Step step, String testName, String urlName)
      throws JMSException {
    ObjectMessage message = session.createObjectMessage(
        new ComparatorJobData(runIndexWrapper.get().getCompany(),
            runIndexWrapper.get().getProject(),
            runIndexWrapper.get().getName(), testName, urlName, step));
    message.setJMSCorrelationID(correlationId);
    sender.send(message);
  }

  private boolean hasComparators(Step step) {
    return step.getComparators() != null && !step.getComparators().isEmpty();
  }

  private void updateSuiteUrl(String testName, Url processedUrl) {
    final Optional<Test> test = runIndexWrapper.getTest(testName);
    test.get().addUrl(processedUrl);
  }

  public void addChangeObserver(ChangeObserver observer) {
    changeListeners.add(observer);
  }

  @Override
  public boolean isFinished() {
    return messagesReceivedSuccess.get() + messagesReceivedFailed.get() == messagesToReceive.get();
  }

  @Override
  protected String getQueueInName() {
    return MessagesManager.createFullQueueName("collectorResults");
  }

  @Override
  protected String getQueueOutName() {
    return MessagesManager.createFullQueueName("comparatorJobs");
  }

  @Override
  protected String getStepName() {
    return STEP_NAME;
  }
}
