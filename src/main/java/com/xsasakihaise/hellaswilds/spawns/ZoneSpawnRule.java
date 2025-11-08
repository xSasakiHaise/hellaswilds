package com.xsasakihaise.hellaswilds.spawns;

import java.util.List;

/**
 * Data representation of a single spawn rule. Most fields are left as plain structures so that the
 * embedded web UI can serialise/deserialise them easily.
 */
public class ZoneSpawnRule {
    public String species;
    public int levelMin;
    public int levelMax;
    public double weight;
    public List<String> time;
    public List<String> weather;
    public String form;
    public String size;
    public List<String> ribbons;
    public boolean alphaRibbon;
    public boolean softDespawn;
    public Integer cooldownSeconds;

    public ZoneSpawnRule() {
    }
}
