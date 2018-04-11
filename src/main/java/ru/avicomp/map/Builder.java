package ru.avicomp.map;

/**
 * An abstract base builder.
 * <p>
 * Created by @szuev on 11.04.2018.
 *
 * @param <R> the type of the result of building.
 */
@FunctionalInterface
interface Builder<R> {

    /**
     * Builds an object.
     *
     * @return ready to use result.
     * @throws MapJenaException if building is not possible
     */
    R build() throws MapJenaException;
}
