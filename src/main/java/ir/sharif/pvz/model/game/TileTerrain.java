package ir.sharif.pvz.model.game;

/**
 * What a board tile is made of. Some terrains block planting, some spawn
 * zombies, and slippery ice pushes walking zombies to a neighbouring row.
 */
public enum TileTerrain {
    NORMAL,
    /** Tombstone: unplantable, blocks direct shots until its hp is gone. */
    GRAVE,
    /** Sea tile: only water plants, or anything after a lily pad is placed. */
    WATER,
    /** Water tile covered by a lily pad, so any plant can sit on it. */
    LILY,
    /** Slippery ice that pushes zombies one row up while they cross it. */
    SLIPPERY_UP,
    /** Slippery ice that pushes zombies one row down while they cross it. */
    SLIPPERY_DOWN,
    /** Low tide / necromancy tile: may spawn a zombie at each wave start. */
    SPAWNER
}
