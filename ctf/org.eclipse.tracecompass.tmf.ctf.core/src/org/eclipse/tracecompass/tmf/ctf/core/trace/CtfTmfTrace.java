/*******************************************************************************
 * Copyright (c) 2012, 2015 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Matthew Khouzam - Initial API and implementation
 *   Patrick Tasse - Updated for removal of context clone
 *   Geneviève Bastien - Added the createTimestamp function
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.ctf.core.trace;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;
import static org.eclipse.tracecompass.common.core.NonNullUtils.nullToEmptyString;

import java.io.File;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.common.core.process.ProcessUtils;
import org.eclipse.tracecompass.ctf.core.CTFException;
import org.eclipse.tracecompass.ctf.core.event.CTFClock;
import org.eclipse.tracecompass.ctf.core.event.IEventDeclaration;
import org.eclipse.tracecompass.ctf.core.event.types.StructDeclaration;
import org.eclipse.tracecompass.ctf.core.trace.CTFStreamInput;
import org.eclipse.tracecompass.ctf.core.trace.CTFTrace;
import org.eclipse.tracecompass.ctf.core.trace.CTFTraceReader;
import org.eclipse.tracecompass.ctf.core.trace.ICTFStream;
import org.eclipse.tracecompass.ctf.core.trace.Metadata;
import org.eclipse.tracecompass.internal.tmf.ctf.core.Activator;
import org.eclipse.tracecompass.internal.tmf.ctf.core.trace.iterator.CtfIterator;
import org.eclipse.tracecompass.internal.tmf.ctf.core.trace.iterator.CtfIteratorManager;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfBaseAspects;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.project.model.ITmfPropertiesProvider;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTraceKnownSize;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTraceWithPreDefinedEvents;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TraceValidationStatus;
import org.eclipse.tracecompass.tmf.core.trace.indexer.ITmfPersistentlyIndexable;
import org.eclipse.tracecompass.tmf.core.trace.indexer.ITmfTraceIndexer;
import org.eclipse.tracecompass.tmf.core.trace.indexer.TmfBTreeTraceIndexer;
import org.eclipse.tracecompass.tmf.core.trace.indexer.checkpoint.ITmfCheckpoint;
import org.eclipse.tracecompass.tmf.core.trace.indexer.checkpoint.TmfCheckpoint;
import org.eclipse.tracecompass.tmf.core.trace.location.ITmfLocation;
import org.eclipse.tracecompass.tmf.core.trace.trim.ITmfTrimmableTrace;
import org.eclipse.tracecompass.tmf.ctf.core.CtfConstants;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfLocation;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfLocationInfo;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfTmfContext;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEventFactory;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEventType;
import org.eclipse.tracecompass.tmf.ctf.core.event.aspect.CtfChannelAspect;
import org.eclipse.tracecompass.tmf.ctf.core.event.aspect.CtfCpuAspect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * The CTf trace handler
 *
 * @version 1.0
 * @author Matthew khouzam
 */
public class CtfTmfTrace extends TmfTrace
        implements ITmfPropertiesProvider, ITmfPersistentlyIndexable,
        ITmfTraceWithPreDefinedEvents, ITmfTraceKnownSize, ITmfTrimmableTrace {

    // -------------------------------------------
    // Constants
    // -------------------------------------------

    /**
     * Clock offset property
     *
     * @since 1.2
     */
    public static final String CLOCK_OFFSET = "clock_offset"; //$NON-NLS-1$

    /**
     * Default cache size for CTF traces
     */
    protected static final int DEFAULT_CACHE_SIZE = 50000;

    /**
     * Event aspects available for all CTF traces
     *
     * @since 1.0
     */
    protected static final @NonNull Collection<@NonNull ITmfEventAspect<?>> CTF_ASPECTS = ImmutableList.of(
            TmfBaseAspects.getTimestampAspect(),
            new CtfChannelAspect(),
            new CtfCpuAspect(),
            TmfBaseAspects.getEventTypeAspect(),
            TmfBaseAspects.getContentsAspect());

    /**
     * The Ctf clock unique identifier field
     */
    private static final String CLOCK_HOST_PROPERTY = "uuid"; //$NON-NLS-1$
    private static final int CONFIDENCE = 10;
    private static final int MIN_CONFIDENCE = 1;

    /**
     * This is a reduction factor to avoid overflows.
     */
    private static final long REDUCTION_FACTOR = 4096;

    /**
     * Average CTF event size, used to estimate the trace size. (Inspired by
     * empirical observations with LTTng kernel traces, to avoid hanging at 100%
     * for too long)
     *
     * TODO: Find a more suitable approximation, perhaps per concrete trace type
     * or per trace directly with the metadata
     */
    private static final int CTF_AVG_EVENT_SIZE = 16;

    // -------------------------------------------
    // Fields
    // -------------------------------------------

    private final Map<@NonNull String, @NonNull CtfTmfEventType> fContainedEventTypes = Collections.synchronizedMap(new HashMap<>());

    private final CtfIteratorManager fIteratorManager = new CtfIteratorManager(this);

    private final @NonNull CtfTmfEventFactory fEventFactory;

    /** Reference to the CTF Trace */
    private CTFTrace fTrace;

    // -------------------------------------------
    // Constructor
    // -------------------------------------------

    /**
     * Default constructor
     */
    public CtfTmfTrace() {
        super();

        /* Use default event factory */
        fEventFactory = CtfTmfEventFactory.instance();
    }

    /**
     * Constructor for sub-classes to specify their own event factory.
     *
     * @param eventFactory
     *            The event factory to use to generate trace events
     * @since 2.0
     */
    protected CtfTmfTrace(@NonNull CtfTmfEventFactory eventFactory) {
        super();
        fEventFactory = eventFactory;
    }

    // -------------------------------------------
    // TmfTrace Overrides
    // -------------------------------------------
    /**
     * Method initTrace.
     *
     * @param resource
     *            The resource associated with this trace
     * @param path
     *            The path to the trace file
     * @param eventType
     *            The type of events that will be read from this trace
     * @throws TmfTraceException
     *             If something went wrong while reading the trace
     */
    @Override
    public void initTrace(final IResource resource, final String path, final Class<? extends ITmfEvent> eventType)
            throws TmfTraceException {
        /*
         * Set the cache size. This has to be done before the call to super()
         * because the super needs to know the cache size.
         */
        setCacheSize();

        super.initTrace(resource, path, eventType);

        try {
            this.fTrace = new CTFTrace(path);
            CtfTmfContext ctx;
            /* Set the start and (current) end times for this trace */
            ctx = (CtfTmfContext) seekEvent(0L);
            CtfTmfEvent event = getNext(ctx);
            if ((ctx.getLocation().equals(CtfIterator.NULL_LOCATION)) || (ctx.getCurrentEvent() == null)) {
                /* Handle the case where the trace is empty */
                this.setStartTime(TmfTimestamp.BIG_BANG);
            } else {
                final ITmfTimestamp curTime = event.getTimestamp();
                this.setStartTime(curTime);
                this.setEndTime(curTime);
            }
            /*
             * Register every event type. When you call getType, it will
             * register a trace to that type in the TmfEventTypeManager
             */
            try (CtfIterator iter = fIteratorManager.getIterator(ctx)) {
                Set<@NonNull ITmfEventField> streamContextNames = new HashSet<>();
                for (IEventDeclaration ied : iter.getEventDeclarations()) {
                    CtfTmfEventType ctfTmfEventType = fContainedEventTypes.get(ied.getName());
                    if (ctfTmfEventType == null) {
                        List<ITmfEventField> content = new ArrayList<>();

                        /* Should only return null the first time */
                        final StructDeclaration fields = ied.getFields();
                        if (fields != null) {
                            for (String fieldName : fields.getFieldsList()) {
                                content.add(new TmfEventField(checkNotNull(fieldName), null, null));
                            }
                        }

                        /* Add stream contexts */
                        final StructDeclaration streamContexts = ied.getStream().getEventContextDecl();
                        if (streamContextNames.isEmpty()) {
                            if (streamContexts != null) {
                                for (String fieldName : streamContexts.getFieldsList()) {
                                    streamContextNames.add(new TmfEventField(checkNotNull(CtfConstants.CONTEXT_FIELD_PREFIX + fieldName), null, null));
                                }
                            }
                        }
                        content.addAll(streamContextNames);

                        if (!content.isEmpty()) {
                            ITmfEventField contentTree = new TmfEventField(
                                    ITmfEventField.ROOT_FIELD_ID,
                                    null,
                                    content.toArray(new ITmfEventField[content.size()]));

                            ctfTmfEventType = new CtfTmfEventType(checkNotNull(ied.getName()), contentTree);
                            fContainedEventTypes.put(ctfTmfEventType.getName(), ctfTmfEventType);
                        }
                    }
                }
            }
            ctx.dispose();
        } catch (final CTFException e) {
            /*
             * If it failed at the init(), we can assume it's because the file
             * was not found or was not recognized as a CTF trace. Throw into
             * the new type of exception expected by the rest of TMF.
             */
            throw new TmfTraceException(e.getMessage(), e);
        }
    }

    @Override
    public synchronized void dispose() {
        fIteratorManager.dispose();
        if (fTrace != null) {
            fTrace = null;
        }
        super.dispose();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default implementation of a CTF trace.
     *
     * Firstly a weak validation of the metadata is done to determine if the
     * path is actually for a CTF trace. After that a full validation is done.
     *
     * If the weak and full validation are successful the confidence is set to
     * 10.
     *
     * If the weak validation was successful, but the full validation fails a
     * TraceValidationStatus with severity warning and confidence of 1 is
     * returned.
     *
     * If both weak and full validation fails an error status is returned.
     */
    @Override
    public IStatus validate(final IProject project, final String path) {
        boolean isMetadataFile = false;
        try {
            isMetadataFile = Metadata.preValidate(path);
        } catch (final CTFException e) {
            return new Status(IStatus.ERROR, Activator.PLUGIN_ID, Messages.CtfTmfTrace_ReadingError + ": " + e.toString(), e); //$NON-NLS-1$
        }

        if (isMetadataFile) {
            // Trace is pre-validated, continue will full validation
            try {
                final CTFTrace trace = new CTFTrace(path);
                if (!trace.majorIsSet()) {
                    if (isMetadataFile) {
                        return new TraceValidationStatus(MIN_CONFIDENCE, IStatus.WARNING, Activator.PLUGIN_ID, Messages.CtfTmfTrace_MajorNotSet, null);
                    }
                    return new Status(IStatus.ERROR, Activator.PLUGIN_ID, Messages.CtfTmfTrace_MajorNotSet);
                }

                // Validate using reader initialization
                try (CTFTraceReader ctfTraceReader = new CTFTraceReader(trace)) {
                    // do nothing
                }

                // Trace is validated, return with confidence
                return new CtfTraceValidationStatus(CONFIDENCE, Activator.PLUGIN_ID, trace.getEnvironment());

            } catch (final CTFException | BufferOverflowException e) {
                // return warning since it's a CTF trace but with errors in it
                return new TraceValidationStatus(MIN_CONFIDENCE, IStatus.WARNING, Activator.PLUGIN_ID, Messages.CtfTmfTrace_ReadingError + ": " + e.toString(), e); //$NON-NLS-1$
            }
        }
        return new Status(IStatus.ERROR, Activator.PLUGIN_ID, Messages.CtfTmfTrace_ReadingError);
    }

    @Override
    public Iterable<ITmfEventAspect<?>> getEventAspects() {
        return CTF_ASPECTS;
    }

    /**
     * Method getCurrentLocation. This is not applicable in CTF
     *
     * @return null, since the trace has no knowledge of the current location
     * @see org.eclipse.tracecompass.tmf.core.trace.ITmfTrace#getCurrentLocation()
     */
    @Override
    public ITmfLocation getCurrentLocation() {
        return null;
    }

    @Override
    public double getLocationRatio(ITmfLocation location) {
        final CtfLocation curLocation = (CtfLocation) location;
        final long startTime = getStartTime().getValue();
        final double diff = curLocation.getLocationInfo().getTimestamp() - startTime;
        final double total = getEndTime().getValue() - startTime;
        return Math.max(0.0, Math.min(1.0, diff / total));
    }

    /**
     * Method seekEvent.
     *
     * @param location
     *            ITmfLocation<?>
     * @return ITmfContext
     */
    @Override
    public synchronized ITmfContext seekEvent(final ITmfLocation location) {
        CtfLocation currentLocation = (CtfLocation) location;
        CtfTmfContext context = new CtfTmfContext(this);
        if (fTrace == null) {
            context.setLocation(null);
            context.setRank(ITmfContext.UNKNOWN_RANK);
            return context;
        }
        /*
         * The rank is set to 0 if the iterator seeks the beginning. If not, it
         * will be set to UNKNOWN_RANK, since CTF traces don't support seeking
         * by rank for now.
         */
        if (currentLocation == null) {
            currentLocation = new CtfLocation(new CtfLocationInfo(0L, 0L));
            context.setRank(0);
        } else {
            context.setRank(ITmfContext.UNKNOWN_RANK);
        }
        /* This will seek and update the location after the seek */
        context.setLocation(currentLocation);
        return context;
    }

    @Override
    public synchronized ITmfContext seekEvent(double ratio) {
        CtfTmfContext context = new CtfTmfContext(this);
        if (fTrace == null) {
            context.setLocation(null);
            context.setRank(ITmfContext.UNKNOWN_RANK);
            return context;
        }
        final long end = getEndTime().getValue();
        final long start = getStartTime().getValue();
        final long diff = end - start;
        final long ratioTs = Math.round(diff * ratio) + start;
        context.seek(ratioTs);
        context.setRank(ITmfContext.UNKNOWN_RANK);
        return context;
    }

    /**
     * Method readNextEvent.
     *
     * @param context
     *            ITmfContext
     * @return CtfTmfEvent
     * @see org.eclipse.tracecompass.tmf.core.trace.ITmfTrace#getNext(ITmfContext)
     */
    @Override
    public synchronized CtfTmfEvent getNext(final ITmfContext context) {
        if (fTrace == null) {
            return null;
        }
        CtfTmfEvent event = null;
        if (context instanceof CtfTmfContext) {
            if (context.getLocation() == null || CtfLocation.INVALID_LOCATION.equals(context.getLocation().getLocationInfo())) {
                return null;
            }
            CtfTmfContext ctfContext = (CtfTmfContext) context;
            event = ctfContext.getCurrentEvent();

            if (event != null) {
                updateAttributes(context, event);
                ctfContext.advance();
                ctfContext.increaseRank();
            }
        }

        return event;
    }

    /**
     * Ctf traces have a clock with a unique uuid that will be used to identify
     * the host. Traces with the same clock uuid will be known to have been made
     * on the same machine.
     *
     * Note: uuid is an optional field, it may not be there for a clock.
     */
    @Override
    public String getHostId() {
        CTFClock clock = fTrace.getClock();
        if (clock != null) {
            String clockHost = (String) clock.getProperty(CLOCK_HOST_PROPERTY);
            if (clockHost != null) {
                return clockHost;
            }
        }
        return super.getHostId();
    }

    /**
     * Get the CTF environment variables defined in this CTF trace, in <name,
     * value> form. This comes from the trace's CTF metadata.
     *
     * @return The CTF environment
     */
    public Map<String, String> getEnvironment() {
        return fTrace.getEnvironment();
    }

    // -------------------------------------------
    // ITmfPropertiesProvider
    // -------------------------------------------

    /**
     * @since 2.0
     */
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.putAll(fTrace.getEnvironment());
        properties.put(CLOCK_OFFSET, Long.toUnsignedString(fTrace.getOffset()));
        properties.put(Messages.CtfTmfTrace_HostID, getHostId());
        return properties;
    }

    // -------------------------------------------
    // Clocks
    // -------------------------------------------

    /**
     * gets the clock offset
     *
     * @return the clock offset in ns
     */
    public long getOffset() {
        if (fTrace != null) {
            return fTrace.getOffset();
        }
        return 0;
    }

    /**
     * Convert a CTF timestamp in CPU cycles to its equivalent in nanoseconds
     * for this trace.
     *
     * @param cycles
     *            The timestamp in cycles
     * @return The timestamp in nanoseconds
     */
    public long timestampCyclesToNanos(long cycles) {
        return fTrace.timestampCyclesToNanos(cycles);
    }

    /**
     * Convert a CTF timestamp in nanoseconds to its equivalent in CPU cycles
     * for this trace.
     *
     * @param nanos
     *            The timestamp in nanoseconds
     * @return The timestamp in cycles
     */
    public long timestampNanoToCycles(long nanos) {
        return fTrace.timestampNanoToCycles(nanos);
    }

    /**
     * Gets the list of declared events
     */
    @Override
    public Set<@NonNull CtfTmfEventType> getContainedEventTypes() {
        return ImmutableSet.copyOf(fContainedEventTypes.values());
    }

    /**
     * Register an event type to this trace.
     *
     * Public visibility so that {@link CtfTmfEvent#getType} can call it.
     *
     * FIXME This could probably be made cleaner?
     *
     * @param eventType
     *            The event type to register
     */
    public void registerEventType(CtfTmfEventType eventType) {
        fContainedEventTypes.put(eventType.getName(), eventType);
    }

    // -------------------------------------------
    // Parser
    // -------------------------------------------

    @Override
    public CtfTmfEvent parseEvent(ITmfContext context) {
        CtfTmfEvent event = null;
        if (context instanceof CtfTmfContext) {
            final ITmfContext tmpContext = seekEvent(context.getLocation());
            event = getNext(tmpContext);
        }
        return event;
    }

    /**
     * Sets the cache size for a CtfTmfTrace.
     */
    protected void setCacheSize() {
        setCacheSize(DEFAULT_CACHE_SIZE);
    }

    // -------------------------------------------
    // CtfIterator factory methods
    // -------------------------------------------

    /**
     * Get the event factory for this trace to generate new events for it.
     *
     * @return The event factory
     * @since 2.0
     */
    public @NonNull CtfTmfEventFactory getEventFactory() {
        return fEventFactory;
    }

    /**
     * Get an iterator to the trace
     *
     * @return an iterator to the trace
     */
    public ITmfContext createIterator() {
        try {
            return new CtfIterator(fTrace, this);
        } catch (CTFException e) {
            Activator.getDefault().logError(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Get an iterator to the trace, , which will initially point to the given
     * location/rank.
     *
     * @param ctfLocationData
     *            The initial timestamp the iterator will be pointing to
     * @param rank
     *            The initial rank
     * @return The new iterator
     */
    public ITmfContext createIterator(CtfLocationInfo ctfLocationData, long rank) {
        try {
            return new CtfIterator(fTrace, this, ctfLocationData, rank);
        } catch (CTFException e) {
            Activator.getDefault().logError(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Create the 'CtfIterator' object from a CtfTmfContext.
     *
     * @param context
     *            The iterator will initially be pointing to this context
     * @return A new CtfIterator object
     * @since 1.0
     */
    public ITmfContext createIteratorFromContext(CtfTmfContext context) {
        return fIteratorManager.getIterator(context);
    }

    /**
     * Dispose an iterator that was create with
     * {@link #createIteratorFromContext}
     *
     * @param context
     *            The last context that was pointed to by the iterator (this is
     *            the 'key' to find the correct iterator to dispose).
     * @since 1.0
     */
    public void disposeContext(CtfTmfContext context) {
        fIteratorManager.removeIterator(context);
    }

    // ------------------------------------------------------------------------
    // Timestamp transformation functions
    // ------------------------------------------------------------------------

    /**
     * @since 1.0
     */
    @Override
    public @NonNull ITmfTimestamp createTimestamp(long ts) {
        return TmfTimestamp.fromNanos(getTimestampTransform().transform(ts));
    }

    private static int fCheckpointSize = -1;

    @Override
    public synchronized int getCheckpointSize() {
        if (fCheckpointSize == -1) {
            TmfCheckpoint c = new TmfCheckpoint(TmfTimestamp.fromNanos(0), new CtfLocation(0, 0), 0);
            ByteBuffer b = ByteBuffer.allocate(ITmfCheckpoint.MAX_SERIALIZE_SIZE);
            b.clear();
            c.serialize(b);
            fCheckpointSize = b.position();
        }

        return fCheckpointSize;
    }

    @Override
    protected ITmfTraceIndexer createIndexer(int interval) {
        return new TmfBTreeTraceIndexer(this, interval);
    }

    @Override
    public ITmfLocation restoreLocation(ByteBuffer bufferIn) {
        return new CtfLocation(bufferIn);
    }

    @Override
    public boolean isComplete() {
        if (getResource() == null) {
            return true;
        }

        String host = null;
        String port = null;
        String sessionName = null;
        try {
            host = getResource().getPersistentProperty(CtfConstants.LIVE_HOST);
            port = getResource().getPersistentProperty(CtfConstants.LIVE_PORT);
            sessionName = getResource().getPersistentProperty(CtfConstants.LIVE_SESSION_NAME);
        } catch (CoreException e) {
            Activator.getDefault().logError(e.getMessage(), e);
            // Something happened to the resource, assume we won't get any more
            // data from it
            return true;
        }
        return host == null || port == null || sessionName == null;
    }

    @Override
    public void setComplete(final boolean isComplete) {
        super.setComplete(isComplete);
        try {
            if (isComplete) {
                getResource().setPersistentProperty(CtfConstants.LIVE_HOST, null);
                getResource().setPersistentProperty(CtfConstants.LIVE_PORT, null);
                getResource().setPersistentProperty(CtfConstants.LIVE_SESSION_NAME, null);
            }
        } catch (CoreException e) {
            Activator.getDefault().logError(e.getMessage(), e);
        }
    }

    /**
     * @return the number of estimated chunks of events read. This reads the
     *         file size of the trace and divides it by a factor and the average
     *         event size, this is not accurate but can give a ball park figure
     *         of how much is done.
     * @since 2.1
     */
    @Override
    public int size() {
        long size = 0;
        CTFTrace trace = fTrace;
        if (trace != null) {
            Iterable<ICTFStream> streams = trace.getStreams();
            for (ICTFStream stream : streams) {
                for (CTFStreamInput si : stream.getStreamInputs()) {
                    size += si.getFile().length();
                }
            }
        }
        return (int) (size / REDUCTION_FACTOR / CTF_AVG_EVENT_SIZE);
    }

    /**
     * @return the number of events divided a reduction factor. Is monotonic.
     * @since 2.1
     */
    @Override
    public int progress() {
        return (int) (getNbEvents() / REDUCTION_FACTOR);
    }

    /**
     * Perform stream intersection on the current trace. This means trimming the
     * trace to the largest time range for which all CTF streams have events
     * present in them.
     *
     * Since the initial trace needs to be imported/initialized in Trace
     * Compass, it works by creating a new trace which results from the stream
     * intersection operation.
     *
     * A typical use case is for analyzing snapshot traces, where some
     * low-activity streams may go back in time very far, and others with more
     * events don't go very far in time. By doing a stream intersection we drop
     * events from less interesting periods.
     *
     * @param destinationPath
     *            The location where the new trace will be created.
     * @param monitor
     *            Progress monitor for cases where the operation is ran from
     *            inside a Job. You can use a
     *            {@link org.eclipse.core.runtime.NullProgressMonitor} if none
     *            is available.
     * @throws CoreException
     *             Optional exception indicating an error during the execution
     *             of the operation.
     * @since 2.2
     */
    public void streamIntersection(@NonNull Path destinationPath,  @NonNull IProgressMonitor monitor) throws CoreException {
        /* Perform the stream intersection reading + outputting by Babeltrace */
        // TODO Just doing "cp" for now

        // TODO Querying the stream ranges could be done on the Trace Compass
        // side. However since we need Babeltrace atm for
        // the trace trimming, we might as well just call Babeltrace's stream
        // intersection operation.
        // If a Babeltrace-free trim() is implemented, this here could be
        // modified to call trim() instead.
        String originPath = getPath();
        if (!originPath.endsWith(File.separator)) {
            originPath = originPath + File.separator;
        }
        List<@NonNull String> command = Arrays.asList("bash", "-c", "cp -R " + originPath + "* " + destinationPath.toString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        ProcessUtils.getOutputFromCommandCancellable(command,
                monitor,
                "Invoking external tool",
                // We don't care about the output atm
                (r, m) -> Collections.emptyList());
    }

    /**
     * @since 2.2
     */
    @Override
    public void trim(@NonNull TmfTimeRange range, @NonNull Path destinationPath, @NonNull IProgressMonitor monitor) throws CoreException {
        /* Trim and save the new trace */
        String originPath = getPath();
        if (!originPath.endsWith(File.separator)) {
            originPath = originPath + File.separator;
        }
        // Dummy 'cp' command that just copies the trace
//        List<@NonNull String> command = Arrays.asList("bash", "-c", "cp -R " + originPath + "* " + destinationPath.toString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
//        return;

        List<@NonNull String> command = Arrays.asList("bash", "-c", //$NON-NLS-1$ //$NON-NLS-2$
                "babeltrace --plugin-path /usr/local/lib/babeltrace/plugins/ -B " + range.getStartTime().toNanos() //$NON-NLS-1$
                + " -E " + range.getEndTime().toNanos() //$NON-NLS-1$
                + " \"" + originPath + '\"' //$NON-NLS-1$
                + " --sink writer.writer --path \"" + destinationPath.toString() + '\"' //$NON-NLS-1$
                );

        ProcessUtils.getOutputFromCommandCancellable(command,
                monitor,
                nullToEmptyString(Messages.CtfTmfTrace_InvokingBabeltrace),
                // We don't care about the output atm
                (r, m) -> Collections.emptyList());

        /*
         * Babeltracecutting dumps the files in a sub-directory called
         * "trace_000". Bring those back to the level we created, and delete
         * this now-empty sub-directory.
         */
        try {
            Path trace000Path = destinationPath.resolve("trace_000"); //$NON-NLS-1$
            Files.walk(trace000Path, 1).forEach(path -> System.out.println(path.toString()));

            Files.walk(trace000Path, 1)
                    /* Don't move the "trace_000" dir itself */
                    .filter(path -> path != trace000Path)
                    .forEach(path -> {
                try {
                    Files.move(path, destinationPath.resolve(path.getFileName()));
                } catch (IOException e) {
                    System.out.println("error! " + e.getMessage());
                    /*
                     * Should not happen because we have checked all permissions
                     * and the target directory should be empty.
                     */
                }
            });
            FileUtils.deleteDirectory(trace000Path.toFile());
        } catch (IOException e) {
            IStatus status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, Messages.CtfTmfTrace_ErrorMovingFiles, e);
            throw new CoreException(status);
        }
    }

}
