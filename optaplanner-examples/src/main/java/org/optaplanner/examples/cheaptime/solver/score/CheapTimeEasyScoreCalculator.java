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

package org.optaplanner.examples.cheaptime.solver.score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.impl.score.director.easy.EasyScoreCalculator;
import org.optaplanner.examples.cheaptime.domain.CheapTimeSolution;
import org.optaplanner.examples.cheaptime.domain.Machine;
import org.optaplanner.examples.cheaptime.domain.PeriodPowerCost;
import org.optaplanner.examples.cheaptime.domain.Task;
import org.optaplanner.examples.cheaptime.domain.TaskAssignment;
import org.optaplanner.examples.cheaptime.domain.TaskRequirement;
import org.optaplanner.examples.cheaptime.solver.CostCalculator;

public class CheapTimeEasyScoreCalculator implements EasyScoreCalculator<CheapTimeSolution> {

    public HardSoftLongScore calculateScore(CheapTimeSolution solution) {
        if (solution.getGlobalPeriodRangeFrom() != 0) {
            throw new IllegalStateException("The globalPeriodRangeFrom (" + solution.getGlobalPeriodRangeFrom()
                    + ") should be 0.");
        }
        int resourceListSize = solution.getResourceList().size();
        int globalPeriodRangeTo = solution.getGlobalPeriodRangeTo();
        List<Machine> machineList = solution.getMachineList();
        Map<Machine, List<MachinePeriodPart>> machinePeriodListMap
                = new LinkedHashMap<Machine, List<MachinePeriodPart>>(machineList.size());
        for (Machine machine : machineList) {
            List<MachinePeriodPart> machinePeriodList = new ArrayList<MachinePeriodPart>(globalPeriodRangeTo);
            for (int period = 0; period < globalPeriodRangeTo; period++) {
                machinePeriodList.add(new MachinePeriodPart(machine, period, resourceListSize));
            }
            machinePeriodListMap.put(machine, machinePeriodList);
        }
        long softScore = 0L;
        List<PeriodPowerCost> periodPowerCostList = solution.getPeriodPowerCostList();
        for (TaskAssignment taskAssignment : solution.getTaskAssignmentList()) {
            Machine machine = taskAssignment.getMachine();
            Integer startPeriod = taskAssignment.getStartPeriod();
            if (machine != null && startPeriod != null) {
                List<MachinePeriodPart> machinePeriodList = machinePeriodListMap.get(machine);
                int endPeriod = taskAssignment.getEndPeriod();
                for (int period = startPeriod; period < endPeriod; period++) {
                    MachinePeriodPart machinePeriodPart = machinePeriodList.get(period);
                    machinePeriodPart.addTaskAssignment(taskAssignment);
                    PeriodPowerCost periodPowerCost = periodPowerCostList.get(period);
                    softScore -= CostCalculator.multiplyTwoMicros(taskAssignment.getTask().getPowerConsumptionMicros(),
                            periodPowerCost.getPowerCostMicros());
                }
            }
        }
        long hardScore = 0L;
        for (Map.Entry<Machine, List<MachinePeriodPart>> entry : machinePeriodListMap.entrySet()) {
            Machine machine = entry.getKey();
            List<MachinePeriodPart> machinePeriodList = entry.getValue();
            MachinePeriodStatus previousStatus = MachinePeriodStatus.OFF;
            long idleCostMicros = 0L;
            for (int period = 0; period < globalPeriodRangeTo; period++) {
                PeriodPowerCost periodPowerCost = periodPowerCostList.get(period);
                MachinePeriodPart machinePeriodPart = machinePeriodList.get(period);
                boolean active = machinePeriodPart.isActive();
                if (active) {
                    if (previousStatus == MachinePeriodStatus.OFF) {
                        // Spin up
                        softScore -= machine.getSpinUpDownCostMicros();
                    } else if (previousStatus == MachinePeriodStatus.IDLE) {
                        // Pay idle cost
                        softScore -= idleCostMicros;
                        idleCostMicros = 0L;
                    }
                    hardScore += machinePeriodPart.getHardScore();
                    softScore -= CostCalculator.multiplyTwoMicros(machine.getPowerConsumptionMicros(),
                            periodPowerCost.getPowerCostMicros());
                    previousStatus = MachinePeriodStatus.ACTIVE;
                } else {
                    if (previousStatus != MachinePeriodStatus.OFF) {
                        idleCostMicros += CostCalculator.multiplyTwoMicros(machine.getPowerConsumptionMicros(),
                                periodPowerCost.getPowerCostMicros());
                        if (idleCostMicros > machine.getSpinUpDownCostMicros()) {
                            idleCostMicros = 0L;
                            previousStatus = MachinePeriodStatus.OFF;
                        } else {
                            previousStatus = MachinePeriodStatus.IDLE;
                        }
                    }
                }
            }
        }
        return HardSoftLongScore.valueOf(hardScore, softScore);
    }

    private enum MachinePeriodStatus {
        OFF,
        IDLE,
        ACTIVE;
    }

    private class MachinePeriodPart {

        private final Machine machine;
        private final int period;

        private boolean active;
        private List<Integer> resourceAvailableList;

        private MachinePeriodPart(Machine machine, int period, int resourceListSize) {
            this.machine = machine;
            this.period = period;
            active = false;
            resourceAvailableList = new ArrayList<Integer>(resourceListSize);
            for (int i = 0; i < resourceListSize; i++) {
                resourceAvailableList.add(machine.getMachineCapacityList().get(i).getCapacity());
            }
        }

        public boolean isActive() {
            return active;
        }

        public void addTaskAssignment(TaskAssignment taskAssignment) {
            active = true;
            Task task = taskAssignment.getTask();
            for (int i = 0; i < resourceAvailableList.size(); i++) {
                int resourceAvailable = resourceAvailableList.get(i);
                TaskRequirement taskRequirement = task.getTaskRequirementList().get(i);
                resourceAvailableList.set(i, resourceAvailable - taskRequirement.getResourceUsage());
            }
        }

        public long getHardScore() {
            long hardScore = 0L;
            for (int resourceAvailable : resourceAvailableList) {
                if (resourceAvailable < 0) {
                    hardScore += (long) resourceAvailable;
                }
            }
            return hardScore;
        }

    }

}