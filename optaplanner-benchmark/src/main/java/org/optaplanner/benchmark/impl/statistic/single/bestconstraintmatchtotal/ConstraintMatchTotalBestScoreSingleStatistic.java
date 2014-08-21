/*
 * Copyright 2014 JBoss Inc
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

package org.optaplanner.benchmark.impl.statistic.single.bestconstraintmatchtotal;

import java.awt.BasicStroke;
import java.awt.Paint;
import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.YIntervalRenderer;
import org.jfree.data.xy.XYIntervalSeries;
import org.jfree.data.xy.XYIntervalSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.optaplanner.benchmark.config.statistic.SingleStatisticType;
import org.optaplanner.benchmark.impl.report.BenchmarkReport;
import org.optaplanner.benchmark.impl.result.SingleBenchmarkResult;
import org.optaplanner.benchmark.impl.statistic.PureSingleStatistic;
import org.optaplanner.benchmark.impl.statistic.common.MillisecondsSpentNumberFormat;
import org.optaplanner.benchmark.impl.statistic.movecountperstep.MoveCountPerStepSingleStatistic;
import org.optaplanner.benchmark.impl.statistic.movecountperstep.MoveCountPerStepStatisticPoint;
import org.optaplanner.benchmark.impl.statistic.single.pickedmovetypebestscore.PickedMoveTypeBestScoreDiffStatisticPoint;
import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.constraint.ConstraintMatchTotal;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchPhaseScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchStepScope;
import org.optaplanner.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import org.optaplanner.core.impl.phase.scope.AbstractPhaseScope;
import org.optaplanner.core.impl.phase.scope.AbstractStepScope;
import org.optaplanner.core.impl.score.ScoreUtils;
import org.optaplanner.core.impl.score.definition.ScoreDefinition;
import org.optaplanner.core.impl.solver.DefaultSolver;

@XStreamAlias("constraintMatchTotalBestScoreSingleStatistic")
public class ConstraintMatchTotalBestScoreSingleStatistic extends PureSingleStatistic<ConstraintMatchTotalBestScoreStatisticPoint> {

    @XStreamOmitField
    private ConstraintMatchTotalBestScoreSingleStatisticListener listener;

    @XStreamOmitField
    protected List<File> graphFileList = null;

    public ConstraintMatchTotalBestScoreSingleStatistic(SingleBenchmarkResult singleBenchmarkResult) {
        super(singleBenchmarkResult, SingleStatisticType.CONSTRAINT_MATCH_TOTAL_BEST_SCORE);
        listener = new ConstraintMatchTotalBestScoreSingleStatisticListener();
    }

    /**
     * @return never null
     */
    public List<File> getGraphFileList() {
        return graphFileList;
    }

    // ************************************************************************
    // Lifecycle methods
    // ************************************************************************

    public void open(Solver solver) {
        ((DefaultSolver) solver).addPhaseLifecycleListener(listener);
    }

    public void close(Solver solver) {
        ((DefaultSolver) solver).removePhaseLifecycleListener(listener);
    }

    private class ConstraintMatchTotalBestScoreSingleStatisticListener extends PhaseLifecycleListenerAdapter {

        @Override
        public void stepEnded(AbstractStepScope stepScope) {
            if (stepScope instanceof LocalSearchStepScope) {
                localSearchStepEnded((LocalSearchStepScope) stepScope);
            }
        }

        private void localSearchStepEnded(LocalSearchStepScope stepScope) {
            if (stepScope.getBestScoreImproved()) {
                long timeMillisSpent = stepScope.getPhaseScope().calculateSolverTimeMillisSpent();
                for (ConstraintMatchTotal constraintMatchTotal
                        : stepScope.getScoreDirector().getConstraintMatchTotals()) {
                    pointList.add(new ConstraintMatchTotalBestScoreStatisticPoint(
                            timeMillisSpent,
                            constraintMatchTotal.getConstraintPackage(),
                            constraintMatchTotal.getConstraintName(),
                            constraintMatchTotal.getScoreLevel(),
                            constraintMatchTotal.getConstraintMatchCount(),
                            constraintMatchTotal.getWeightTotalAsNumber().doubleValue()));
                }
            }
        }

    }

    // ************************************************************************
    // CSV methods
    // ************************************************************************

    @Override
    protected String getCsvHeader() {
        return PickedMoveTypeBestScoreDiffStatisticPoint.buildCsvLine(
                "timeMillisSpent", "constraintPackage", "constraintName", "scoreLevel",
                "constraintMatchCount", "weightTotal");
    }

    @Override
    protected ConstraintMatchTotalBestScoreStatisticPoint createPointFromCsvLine(ScoreDefinition scoreDefinition,
            List<String> csvLine) {
        return new ConstraintMatchTotalBestScoreStatisticPoint(Long.valueOf(csvLine.get(0)),
                csvLine.get(1), csvLine.get(2), Integer.valueOf(csvLine.get(3)),
                Integer.valueOf(csvLine.get(4)), Double.valueOf(csvLine.get(5)));
    }

    // ************************************************************************
    // Write methods
    // ************************************************************************

    @Override
    public void writeGraphFiles(BenchmarkReport benchmarkReport) {
        List<Map<String, XYSeries>> constraintIdToWeightSeriesMapList
                = new ArrayList<Map<String, XYSeries>>(BenchmarkReport.CHARTED_SCORE_LEVEL_SIZE);
        for (ConstraintMatchTotalBestScoreStatisticPoint point : getPointList()) {
            int scoreLevel = point.getScoreLevel();
            if (scoreLevel >= BenchmarkReport.CHARTED_SCORE_LEVEL_SIZE) {
                continue;
            }
            if (scoreLevel >= constraintIdToWeightSeriesMapList.size()) {
                constraintIdToWeightSeriesMapList.add(new LinkedHashMap<String, XYSeries>());
            }
            Map<String, XYSeries> constraintIdToWeightSeriesMap = constraintIdToWeightSeriesMapList.get(scoreLevel);
            if (constraintIdToWeightSeriesMap == null) {
                constraintIdToWeightSeriesMap = new LinkedHashMap<String, XYSeries>();
                constraintIdToWeightSeriesMapList.set(scoreLevel, constraintIdToWeightSeriesMap);
            }
            String constraintId = point.getConstraintPackage() + ":" + point.getConstraintName();
            XYSeries weightSeries = constraintIdToWeightSeriesMap.get(constraintId);
            if (weightSeries == null) {
                weightSeries = new XYSeries(point.getConstraintName() + " weight");
                constraintIdToWeightSeriesMap.put(constraintId, weightSeries);
            }
            long timeMillisSpent = point.getTimeMillisSpent();
            weightSeries.add(timeMillisSpent, point.getWeightTotal());
        }
        graphFileList = new ArrayList<File>(constraintIdToWeightSeriesMapList.size());
        for (int scoreLevelIndex = 0; scoreLevelIndex < constraintIdToWeightSeriesMapList.size(); scoreLevelIndex++) {
            XYPlot plot = createPlot(benchmarkReport, scoreLevelIndex);
            XYItemRenderer renderer = new XYLineAndShapeRenderer(true, false);
            plot.setRenderer(renderer);
            XYSeriesCollection seriesCollection = new XYSeriesCollection();
            for (XYSeries series : constraintIdToWeightSeriesMapList.get(scoreLevelIndex).values()) {
                seriesCollection.addSeries(series);
            }
            plot.setDataset(seriesCollection);
            JFreeChart chart = new JFreeChart(
                    singleBenchmarkResult.getName() + " constraint match total best score diff level " + scoreLevelIndex + " statistic",
                    JFreeChart.DEFAULT_TITLE_FONT, plot, true);
            graphFileList.add(writeChartToImageFile(chart,
                    "ConstraintMatchTotalBestScoreStatisticLevel" + scoreLevelIndex));
        }
    }

    private XYPlot createPlot(BenchmarkReport benchmarkReport, int scoreLevelIndex) {
        Locale locale = benchmarkReport.getLocale();
        NumberAxis xAxis = new NumberAxis("Time spent");
        xAxis.setNumberFormatOverride(new MillisecondsSpentNumberFormat(locale));
        NumberAxis yAxis = new NumberAxis("Constraint match total weight level " + scoreLevelIndex);
        yAxis.setNumberFormatOverride(NumberFormat.getInstance(locale));
        yAxis.setAutoRangeIncludesZero(true);
        XYPlot plot = new XYPlot(null, xAxis, yAxis, null);
        plot.setOrientation(PlotOrientation.VERTICAL);
        return plot;
    }

}