package com.wudsn.tools.rmt.model;

/**
 * A real deep-copy snapshot of every instrument, analogous to
 * {@link TracksAll} - used by {@link Undo} to save/restore all instrument
 * state at once. C++'s {@code TInstrumentsAll}/{@code GetInstrumentsAll()}
 * is a zero-copy reinterpret-cast view (skipped when {@link Instruments}
 * was first ported - see its own javadoc), not a real copy; this is a new,
 * additive capability built specifically for {@code Undo}'s actual need
 * (independent snapshot/restore), not a reintroduction of the skipped
 * method.
 */
public final class InstrumentsAll {

	public final Instrument[] instruments;

	public InstrumentsAll() {
		instruments = new Instrument[Instruments.INSTRSNUM];
		for (int i = 0; i < instruments.length; i++) {
			instruments[i] = new Instrument();
		}
	}

	public void copyFrom(InstrumentsAll other) {
		for (int i = 0; i < instruments.length; i++) {
			instruments[i].copyFrom(other.instruments[i]);
		}
	}
}
