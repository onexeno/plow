package com.breakersoft.plow.dao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.breakersoft.plow.FrameRange;
import com.breakersoft.plow.Job;
import com.breakersoft.plow.Layer;
import com.breakersoft.plow.thrift.LayerSpecT;

public interface LayerDao {

    Layer create(Job job, LayerSpecT layer, int order);

    Layer get(Job job, String name);

    Layer get(UUID id);

    FrameRange getFrameRange(Layer layer);

    Layer get(Job job, int idx);

    boolean isFinished(Layer layer);

    void addOutput(Layer layer, String path, Map<String, String> attrs);

    void setMinCores(Layer layer, int cores);

    void setMaxCores(Layer layer, int cores);

    void setMinRam(Layer layer, int memory);

    void setThreadable(Layer layer, boolean threadable);

    void setTags(Layer layer, List<String> tags);
}
