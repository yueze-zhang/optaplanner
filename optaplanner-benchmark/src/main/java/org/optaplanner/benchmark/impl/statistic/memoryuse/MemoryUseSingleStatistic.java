/*
 * Copyright 2011 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.benchmark.impl.statistic.memoryuse;

import java.util.ArrayList;
import java.util.List;

import org.optaplanner.benchmark.impl.result.SingleBenchmarkResult;
import org.optaplanner.benchmark.impl.statistic.AbstractSingleStatistic;
import org.optaplanner.benchmark.impl.statistic.ProblemStatisticType;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.impl.phase.event.SolverPhaseLifecycleListenerAdapter;
import org.optaplanner.core.impl.phase.step.AbstractStepScope;
import org.optaplanner.core.impl.score.definition.ScoreDefinition;
import org.optaplanner.core.impl.solver.DefaultSolver;

public class MemoryUseSingleStatistic extends AbstractSingleStatistic<MemoryUseSingleStatisticPoint> {

    private MemoryUseSingleStatisticListener listener = new MemoryUseSingleStatisticListener();

    private long timeMillisThresholdInterval;
    private long nextTimeMillisThreshold;

    private List<MemoryUseSingleStatisticPoint> pointList = new ArrayList<MemoryUseSingleStatisticPoint>();

    public MemoryUseSingleStatistic(SingleBenchmarkResult singleBenchmarkResult) {
        this(singleBenchmarkResult, 1000L);
    }

    public MemoryUseSingleStatistic(SingleBenchmarkResult singleBenchmarkResult, long timeMillisThresholdInterval) {
        super(singleBenchmarkResult, ProblemStatisticType.MEMORY_USE);
        if (timeMillisThresholdInterval <= 0L) {
            throw new IllegalArgumentException("The timeMillisThresholdInterval (" + timeMillisThresholdInterval
                    + ") must be bigger than 0.");
        }
        this.timeMillisThresholdInterval = timeMillisThresholdInterval;
        nextTimeMillisThreshold = timeMillisThresholdInterval;
    }

    public List<MemoryUseSingleStatisticPoint> getPointList() {
        return pointList;
    }

    // ************************************************************************
    // Worker methods
    // ************************************************************************

    public void open(Solver solver) {
        ((DefaultSolver) solver).addSolverPhaseLifecycleListener(listener);
    }

    public void close(Solver solver) {
        ((DefaultSolver) solver).removeSolverPhaseLifecycleListener(listener);
    }
    
    private class MemoryUseSingleStatisticListener extends SolverPhaseLifecycleListenerAdapter {

        @Override
        public void stepEnded(AbstractStepScope stepScope) {
            long timeMillisSpend = stepScope.getPhaseScope().calculateSolverTimeMillisSpend();
            if (timeMillisSpend >= nextTimeMillisThreshold) {
                pointList.add(new MemoryUseSingleStatisticPoint(timeMillisSpend, MemoryUseMeasurement.create()));

                nextTimeMillisThreshold += timeMillisThresholdInterval;
                if (nextTimeMillisThreshold < timeMillisSpend) {
                    nextTimeMillisThreshold = timeMillisSpend;
                }
            }
        }

    }

    // ************************************************************************
    // CSV methods
    // ************************************************************************

    @Override
    protected List<String> getCsvHeader() {
        return MemoryUseSingleStatisticPoint.buildCsvLine("timeMillisSpend", "usedMemory", "maxMemory");
    }

    @Override
    protected MemoryUseSingleStatisticPoint createPointFromCsvLine(ScoreDefinition scoreDefinition,
            List<String> csvLine) {
        return new MemoryUseSingleStatisticPoint(Long.valueOf(csvLine.get(0)),
                new MemoryUseMeasurement(Long.valueOf(csvLine.get(1)), Long.valueOf(csvLine.get(2))));
    }

}