package net.roguelogix.biggerreactors.classic.helpers;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public abstract class FluidHelper implements IConditionalUpdater {

    private FluidStack[] fluids;
    private int capacity;

    private int ticksSinceLastUpdate;
    private static final int minimumTicksBetweenUpdates = 60;
    private static final int minimumDevianceForUpdate = 50; // at least 50mB difference before we send a fueling update to the client

    int[] fluidLevelAtLastUpdate;

    private static final int FORCE_UPDATE = -1000;
    private int numberOfFluids;

    private boolean separateChambers;

    /**
     * @param separate True if capacity is applied to each fluid separately, false if they should be treated like a single tank with multiple fluids inside.
     */
    public FluidHelper(boolean separate) {
        numberOfFluids = getNumberOfFluidTanks();

        fluids = new FluidStack[numberOfFluids];
        fluidLevelAtLastUpdate = new int[numberOfFluids];

        for (int i = 0; i < numberOfFluids; i++) {
            fluids[i] = null;
            fluidLevelAtLastUpdate[i] = FORCE_UPDATE;
        }

        capacity = 0;
        separateChambers = separate;
    }

    public abstract int getNumberOfFluidTanks();

    protected abstract String[] getNBTTankNames();

    // Implementation: IConditionalUpdater
    public boolean shouldUpdate() {
        ticksSinceLastUpdate++;
        if (minimumTicksBetweenUpdates < ticksSinceLastUpdate) {
            int dev = 0;
            boolean shouldUpdate = false;
            for (int i = 0; i < numberOfFluids && !shouldUpdate; i++) {

                if (fluids[i] == null && fluidLevelAtLastUpdate[i] > 0) {
                    shouldUpdate = true;
                } else if (fluids[i] != null) {
                    if (fluidLevelAtLastUpdate[i] == FORCE_UPDATE) {
                        shouldUpdate = true;
                    } else {
                        dev += Math.abs(fluids[i].getAmount() - fluidLevelAtLastUpdate[i]);
                    }
                }
                // else, both levels are zero, no-op

                if (dev >= minimumDevianceForUpdate) {
                    shouldUpdate = true;
                }
            }

            if (shouldUpdate) {
                resetLastSeenFluidLevels();
            }

            ticksSinceLastUpdate = 0;
            return shouldUpdate;
        }

        return false;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int newCapacity) {
        int oldCapacity = capacity;
        capacity = newCapacity;

        clampContentsToCapacity();
    }

    protected void merge(FluidHelper other) {
        if (other.capacity > capacity) {
            capacity = other.capacity;
            fluids = other.fluids;
        }
    }

    /**
     * @return Total amount of stuff contained, across all fluid tanks
     */
    public int getTotalAmount() {
        int amt = 0;
        for (int i = 0; i < fluids.length; i++) {
            amt += getFluidAmount(i);
        }
        return amt;
    }

    protected CompoundNBT writeToNBT(CompoundNBT destination) {
        String[] tankNames = getNBTTankNames();

        if (tankNames.length != fluids.length) {
            throw new IllegalArgumentException("getNBTTankNames must return the same number of strings as there are fluid stacks");
        }

        FluidStack stack;
        for (int i = 0; i < tankNames.length; i++) {
            stack = fluids[i];
            if (stack != null) {
                destination.put(tankNames[i], stack.writeToNBT(new CompoundNBT()));
            }
        }

        return destination;
    }

    protected void readFromNBT(CompoundNBT data) {
        String[] tankNames = getNBTTankNames();

        if (tankNames.length != fluids.length) {
            throw new IllegalArgumentException("getNBTTankNames must return the same number of strings as there are fluid stacks");
        }

        for (int i = 0; i < tankNames.length; i++) {
            if (data.hasUniqueId(tankNames[i])) {
                fluids[i] = FluidStack.loadFluidStackFromNBT(data.getCompound(tankNames[i]));
                fluidLevelAtLastUpdate[i] = fluids[i].getAmount();
            } else {
                fluids[i] = null;
                fluidLevelAtLastUpdate[i] = 0;
            }
        }
    }

    ////// FLUID HELPERS //////
    protected void setFluid(int idx, FluidStack newFluid) {
        fluids[idx] = newFluid;
    }

    protected int getFluidAmount(int idx) {
        if (fluids[idx] == null) {
            return 0;
        } else {
            return fluids[idx].getAmount();
        }

    }

    protected Fluid getFluidType(int idx) {
        if (fluids[idx] == null) {
            return null;
        } else {
            return fluids[idx].getFluid();
        }
    }

    protected abstract boolean isFluidValidForStack(int stackIdx, Fluid fluid);

    protected boolean canAddToStack(int idx, Fluid incoming) {
        if (idx < 0 || idx >= fluids.length || incoming == null) {
            return false;
        } else if (fluids[idx] == null) {
            return isFluidValidForStack(idx, incoming);
        }
        return fluids[idx].getFluid() == incoming;
    }

    protected boolean canAddToStack(int idx, FluidStack incoming) {
        if (idx < 0 || idx >= fluids.length || incoming == null) {
            return false;
        } else if (fluids[idx] == null) {
            return isFluidValidForStack(idx, incoming.getFluid());
        }
        return fluids[idx].isFluidEqual(incoming);
    }

    protected int addFluidToStack(int idx, int fluidAmount) {
        if (fluids[idx] == null || fluids[idx].getFluid() == null) {
            throw new IllegalArgumentException("Cannot add fluid with only an integer when tank is empty!");
        }

        int amtToAdd = Math.min(fluidAmount, getRemainingSpaceForFluid(idx));

        fluids[idx].setAmount(fluids[idx].getAmount() + amtToAdd);
        return amtToAdd;
    }

    /**
     * Drain some fluid from a given stack
     *
     * @param idx    Index of the stack (FUEL or WASTE)
     * @param amount Nominal amount to drain
     * @return Amount actually drained
     */
    protected int drainFluidFromStack(int idx, Fluid fluid, int amount) {
        if (fluids[idx] == null) {
            return 0;
        }

        if (fluids[idx].getFluid() != fluid) {
            return 0;
        }

        return drainFluidFromStack(idx, amount);
    }

    /**
     * Drain fluid from a given stack, without caring what type it is.
     *
     * @param idx    Index of the stack
     * @param amount Amount to drain
     * @return
     */
    protected int drainFluidFromStack(int idx, int amount) {
        if (fluids[idx] == null) {
            return 0;
        }

        if (fluids[idx].getAmount() <= amount) {
            amount = fluids[idx].getAmount();
            fluids[idx] = null;
        } else {
            fluids[idx].setAmount(fluids[idx].getAmount() - amount);
        }
        return amount;
    }

    protected void clampContentsToCapacity() {
        if (separateChambers) {
            // Clamp each tank to capacity
            for (int i = 0; i < fluids.length; i++) {
                if (fluids[i] != null) {
                    fluids[i].setAmount(Math.min(getCapacity(), fluids[i].getAmount()));
                }
            }
        } else {
            if (getTotalAmount() > capacity) {
                int diff = getTotalAmount() - capacity;

                // Reduce stuff in the tanks. Start with waste, to be nice to players.
                for (int i = fluids.length - 1; i >= 0 && diff > 0; i--) {
                    if (fluids[i] != null) {
                        if (diff > fluids[i].getAmount()) {
                            diff -= fluids[i].getAmount();
                            fluids[i] = null;
                        } else {
                            fluids[i].setAmount(fluids[i].getAmount() - diff);
                            diff = 0;
                        }
                    }
                }
            }
            // Else: nothing to do, no need to clamp
        }
    }

    protected void resetLastSeenFluidLevels() {
        for (int i = 0; i < numberOfFluids; i++) {
            if (fluids[i] == null) {
                fluidLevelAtLastUpdate[i] = 0;
            } else {
                fluidLevelAtLastUpdate[i] = fluids[i].getAmount();
            }
        }
    }

    protected int getRemainingSpaceForFluid(int idx) {
        int containedFluidAmt;
        if (separateChambers) {
            containedFluidAmt = getFluidAmount(idx);
        } else {
            containedFluidAmt = getTotalAmount();
        }

        return getCapacity() - containedFluidAmt;
    }

    // IFluidHandler analogue
    public int fill(int idx, FluidStack incoming, boolean doFill) {
        if (incoming == null || idx < 0 || idx >= fluids.length) {
            return 0;
        }

        if (!canAddToStack(idx, incoming)) {
            return 0;
        }

        int amtToAdd = Math.min(incoming.getAmount(), getRemainingSpaceForFluid(idx));

        if (amtToAdd <= 0) {
            return 0;
        }

        if (!doFill) {
            return amtToAdd;
        }

        if (fluids[idx] == null) {
            fluids[idx] = incoming.copy();
            fluids[idx].setAmount(amtToAdd);
        } else {
            fluids[idx].setAmount(fluids[idx].getAmount() + amtToAdd);
        }

        return amtToAdd;
    }

    public FluidStack drain(int idx, FluidStack resource,
                            boolean doDrain) {
        if (resource == null || resource.getAmount() <= 0 || idx < 0 || idx >= fluids.length) {
            return null;
        }

        Fluid existingFluid = getFluidType(idx);
        if (existingFluid == null || existingFluid != resource.getFluid()) {
            return null;
        }

        FluidStack drained = resource.copy();
        if (!doDrain) {
            drained.setAmount(Math.min(resource.getAmount(), getFluidAmount(idx)));
        } else {
            drained.setAmount(drainFluidFromStack(idx, resource.getAmount()));
        }

        return drained;
    }

    public FluidStack drain(int idx, int maxDrain, boolean doDrain) {
        if (maxDrain <= 0 || idx < 0 || idx >= fluids.length) {
            return null;
        }

        if (getFluidType(idx) == null) {
            return null;
        }

        FluidStack drained = new FluidStack(getFluidType(idx), 0);

        if (!doDrain) {
            drained.setAmount(Math.min(getFluidAmount(idx), maxDrain));
        } else {
            drained.setAmount(drainFluidFromStack(idx, maxDrain));
        }

        return drained;
    }

    public boolean canFill(int idx, Fluid fluid) {
        return canAddToStack(idx, fluid);
    }

    public boolean canDrain(int idx, Fluid fluid) {
        if (fluid == null || idx < 0 || idx >= fluids.length) {
            return false;
        }

        if (fluids[idx] == null) {
            return false;
        }

        return fluids[idx].getFluid() == fluid;
    }

    private static FluidTankInfo[] emptyTankArray = new FluidTankInfo[0];

    public FluidTankInfo[] getTankInfo(int idx) {
        if (idx >= fluids.length) {
            return emptyTankArray;
        }

        FluidTankInfo[] info;

        if (idx < 0) {
            // All tanks
            info = new FluidTankInfo[fluids.length];
            for (int i = 0; i < fluids.length; i++) {
                info[i] = new FluidTankInfo(fluids[i] == null ? null : fluids[i].copy(), getCapacity());
            }

            return info;
        } else {
            info = new FluidTankInfo[1];
            info[0] = new FluidTankInfo(fluids[idx] == null ? null : fluids[idx].copy(), getCapacity());
        }

        return info;
    }

    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Capacity (per): ").append(Integer.toString(getCapacity()));
        String[] tankNames = getNBTTankNames();
        for (int i = 0; i < fluids.length; i++) {
            sb.append("[").append(Integer.toString(i)).append("] ").append(tankNames[i]).append(": ");
            if (fluids[i] == null) {
                sb.append("NULL");
            } else {
                FluidStack stack = fluids[i];
                sb.append(stack.getFluid().getRegistryName()).append(", ").append(Integer.toString(stack.getAmount())).append(" mB");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
