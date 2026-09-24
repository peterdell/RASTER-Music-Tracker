package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ struct TUndoEvent (Undo.h). {@code dataIsArray} (pure
 * C++ memory-management bookkeeping - whether {@code data} needs
 * {@code delete[]} or plain {@code delete}) is dropped entirely; Java's
 * garbage collector makes the distinction moot. {@code data} holds
 * whichever snapshot type matches {@code type} (an {@code int[]}, a
 * {@link Track}, a {@link TracksAll}, an {@link Instrument}, an
 * {@link InstrumentsAll}, a {@link SongData}, or a {@link SongInfo}) -
 * {@link Object}, not a sealed type, matching the C++ {@code void*}
 * design's own lack of type safety here.
 */
final class UndoEvent {

	Part part; // the part in which the editing is performed
	int[] cursor;
	int type; // one of the UndoType.UETYPE_* constants
	int[] pos; // position of changed data
	Object data; // change data
	int separator; // 0 = accumulate continuous changes, 1 = completed change, -1 = more events for one step
}
