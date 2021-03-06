package com.breakersoft.plow.dispatcher.domain;

import java.util.Set;

import com.breakersoft.plow.Defaults;
import com.breakersoft.plow.NodeE;

public class DispatchNode extends NodeE implements DispatchResource {

    private int cores;
    private int memory;
    private Set<String> tags;
    private boolean locked;

    public DispatchNode() { }

    public int getIdleCores() {
        return cores;
    }

    public void setCores(int cores) {
        this.cores = cores;
    }

    public int getIdleRam() {
        return memory;
    }

    public void setMemory(int memory) {
        this.memory = memory;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public void decrement(int cores, int memory) {
        this.cores = this.cores - cores;
        this.memory = this.memory - memory;
    }

    /**
     * Return true if the node has cores and memory available and
     * dispatchable is set to true.
     *
     * @return
     */
    public boolean isDispatchable() {
        if (cores == 0 || memory <= Defaults.MEMORY_MIN_MB) {
            return false;
        }

        if (locked) {
            return false;
        }

        return true;
    }

    public String toString() {
        return String.format("Node: %s [%s] cores:%d mem:%d", getName(), getNodeId(), cores, memory);
    }

    @Override
    public void allocate(int cores, int ram) {
        this.cores = this.cores - cores;
        this.memory = this.memory - ram;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
