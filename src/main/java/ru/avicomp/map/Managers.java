package ru.avicomp.map;

import ru.avicomp.map.spin.MapManagerImpl;

/**
 * The main (static) access point to {@link MapManager} instances.
 * <p>
 * Created by @szuev on 07.04.2018.
 */
public class Managers {

    public static MapManager createMapManager() {
        return new MapManagerImpl();
    }
}
