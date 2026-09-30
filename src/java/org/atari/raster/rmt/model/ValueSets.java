package org.atari.raster.rmt.model;

/**
 * Value set repository container of this package: the class whose name gives
 * {@code ValueSets.properties} - the single properties file that holds the
 * display texts of every {@link com.wudsn.tools.base.repository.ValueSet} in
 * this package, keyed {@code <ClassName>_<id>} (see
 * {@code com.wudsn.tools.base.repository.NLS}). Each value set names this
 * class in its {@code initializeClass(X.class, ValueSets.class)} call.
 *
 * <p>The texts are English only for now; a {@code ValueSets_de.properties}
 * beside this one would be picked up by {@code NLS.initializeLocale}
 * without any code change (see plans/28_VALUE_SETS_PLAN.md).
 */
public class ValueSets extends com.wudsn.tools.base.ValueSets {
}
