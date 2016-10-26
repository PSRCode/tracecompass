/*******************************************************************************
 * Copyright (c) 2009, 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Francois Chouinard - Initial API and implementation
 *   Bernd Hufmann - Update register methods
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.signal;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.tracecompass.internal.tmf.core.Activator;
import org.eclipse.tracecompass.internal.tmf.core.TmfCoreTracer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * This class manages the set of signal listeners and the signals they are
 * interested in. When a signal is broadcasted, the appropriate listeners
 * signal handlers are invoked.
 *
 * @version 1.0
 * @author Francois Chouinard
 */
public class TmfSignalManager {

    // The set of event listeners and their corresponding handler methods.
    // Note: listeners could be restricted to ITmfComponents but there is no
    // harm in letting anyone use this since it is not tied to anything but
    // the signal data type.
    private static Map<Object, Method[]> fListeners = new HashMap<>();
    private static Map<Object, Method[]> fVIPListeners = new HashMap<>();

    /** The outbound blacklist of pair <source, signal> */
    private static Multimap<@NonNull Object, @NonNull Class<? extends TmfSignal>> fOutboundSignalBlacklist = HashMultimap.create();
    /** The inbound blacklist of pair <listener, signal> */
    private static Multimap<@NonNull Object, @NonNull Class<? extends TmfSignal>> fInboundSignalBlacklist = HashMultimap.create();

    // The signal executor for asynchronous signals
    private static final ExecutorService fExecutor = Executors.newSingleThreadExecutor();

    // If requested, add universal signal tracer
    // TODO: Temporary solution: should be enabled/disabled dynamically
    private static boolean fTraceIsActive = false;
    private static TmfSignalTracer fSignalTracer;

    static {
        if (fTraceIsActive) {
            fSignalTracer = TmfSignalTracer.getInstance();
            register(fSignalTracer);
        }
    }

    /**
     * Register an object to the signal manager. This object can then implement
     * handler methods, marked with @TmfSignalHandler and with the expected
     * signal type as parameter.
     *
     * @param listener
     *            The object that will be notified of new signals
     */
    public static synchronized void register(Object listener) {
        deregister(listener); // make sure that listener is only registered once
        Method[] methods = getSignalHandlerMethods(listener);
        if (methods.length > 0) {
            fListeners.put(listener, methods);
        }
    }

    /**
     * Ignore the outbound signal type from the specified source.
     * One can ignore all signals by passing TmfSignal.class as the signal class.
     *
     * @param source
     *            The source object
     * @param signal
     *            The signal class to ignore
     * @since 2.2
     */
    @NonNullByDefault
    public static synchronized void addIgnoredOutboundSignal(Object source, Class<? extends TmfSignal> signal) {
        fOutboundSignalBlacklist.put(source, signal);
    }

    /**
     * Ignore the inbound signal type for the specified listener.
     * All signals can be ignored by passing TmfSignal.class.
     *
     * @param listener
     *            The listener object for which the signal must be ignored
     * @param signal
     *            The signal class to ignore
     * @since 2.2
     */
    @NonNullByDefault
    public static synchronized void addIgnoredInboundSignal(Object listener, Class<? extends TmfSignal> signal) {
        fInboundSignalBlacklist.put(listener, signal);
    }

    /**
     * Remove the signal from the list of ignored outbound signal for the
     * specified source if present.
     *
     * @param source
     *            The source object
     * @param signal
     *            The signal class to remove from the ignore list
     * @since 2.2
     */
    public static synchronized void removeIgnoredOutboundSignal(Object source, Class<? extends TmfSignal> signal) {
        fOutboundSignalBlacklist.remove(source, signal);
    }

    /**
     * Remove the signal from the list of inbound ignored signals for the
     * specified listener if present.
     *
     * @param listener
     *            The listener object
     * @param signal
     *            The signal class to remove from the ignore list
     * @since 2.2
     */
    public static synchronized void removeIgnoredInboundSignal(Object listener, Class<? extends TmfSignal> signal) {
        fInboundSignalBlacklist.remove(listener, signal);
    }


    /**
     * Clear the list of ignored outbound signals for the source.
     *
     * @param source
     *            The source object
     * @since 2.2
     */
    public static synchronized void clearIgnoredOutboundSignalList(Object source) {
        fOutboundSignalBlacklist.removeAll(source);
    }

    /**
     * Clear the list of ignored inbound signals for the listener.
     *
     * @param listener
     *            The listener object
     * @since 2.2
     */
    public static synchronized void clearIgnoredInboundSignalList(Object listener) {
        fInboundSignalBlacklist.removeAll(listener);
    }

    /**
     * Register an object to the signal manager as a "VIP" listener. All VIP
     * listeners will all receive the signal before the manager moves on to the
     * lowly, non-VIP listeners.
     *
     * @param listener
     *            The object that will be notified of new signals
     */
    public static synchronized void registerVIP(Object listener) {
        deregister(listener); // make sure that listener is only registered once
        Method[] methods = getSignalHandlerMethods(listener);
        if (methods.length > 0) {
            fVIPListeners.put(listener, methods);
        }
    }

    /**
     * De-register a listener object from the signal manager. This means that
     * its @TmfSignalHandler methods will no longer be called.
     *
     * @param listener
     *            The object to de-register
     */
    public static synchronized void deregister(Object listener) {
        fVIPListeners.remove(listener);
        fListeners.remove(listener);
        fInboundSignalBlacklist.removeAll(listener);
        fOutboundSignalBlacklist.removeAll(listener);
    }

    /**
     * Returns the list of signal handlers in the listener. Signal handler name
     * is irrelevant; only the annotation (@TmfSignalHandler) is important.
     *
     * @param listener
     * @return
     */
    private static Method[] getSignalHandlerMethods(Object listener) {
        List<Method> handlers = new ArrayList<>();
        Method[] methods = listener.getClass().getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(TmfSignalHandler.class)) {
                handlers.add(method);
            }
        }
        return handlers.toArray(new Method[handlers.size()]);
    }

    static int fSignalId = 0;

    /**
     * Invokes the handling methods that listens to signals of a given type in
     * the current thread.
     *
     * The list of handlers is built on-the-fly to allow for the dynamic
     * creation/deletion of signal handlers. Since the number of signal handlers
     * shouldn't be too high, this is not a big performance issue to pay for the
     * flexibility.
     *
     * For synchronization purposes, the signal is bracketed by two synch
     * signals.
     *
     * @param signal
     *            the signal to dispatch
     */
    public static synchronized void dispatchSignal(TmfSignal signal) {

        /* Check if the source,signal tuple is blacklisted */
        Object source = signal.getSource();
        if (source != null) {
            boolean isBlackListed = fOutboundSignalBlacklist.get(source).stream()
                    .anyMatch(x -> x.isAssignableFrom(signal.getClass()));
            if (isBlackListed) {
                return;
            }
        }

        int signalId = fSignalId++;
        sendSignal(new TmfStartSynchSignal(signalId));
        signal.setReference(signalId);
        sendSignal(signal);
        sendSignal(new TmfEndSynchSignal(signalId));
    }

    /**
     * Invokes the handling methods that listens to signals of a given type
     * in a separate thread which will call
     * {@link TmfSignalManager#dispatchSignal(TmfSignal)}.
     *
     * If a signal is already processed the signal will be queued and
     * dispatched after the ongoing signal finishes.
     *
     * @param signal
     *            the signal to dispatch
     */
    public static void dispatchSignalAsync(final TmfSignal signal) {
        if (!fExecutor.isShutdown()) {
            fExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    dispatchSignal(signal);
                }
            });
        }
    }

    /**
     * Disposes the signal manager
     */
    public static void dispose() {
        fExecutor.shutdown();
    }

    private static void sendSignal(TmfSignal signal) {
        sendSignal(fVIPListeners, signal);
        sendSignal(fListeners, signal);
    }

    private static void sendSignal(Map<Object, Method[]> listeners, TmfSignal signal) {

        if (TmfCoreTracer.isSignalTraced()) {
            TmfCoreTracer.traceSignal(signal, "(start)"); //$NON-NLS-1$
        }

        // Build the list of listener methods that are registered for this signal
        Class<?> signalClass = signal.getClass();

        Map<Object, List<Method>> targets = new HashMap<>();
        targets.clear();
        for (Map.Entry<Object, Method[]> entry : listeners.entrySet()) {
            List<Method> matchingMethods = new ArrayList<>();
            for (Method method : entry.getValue()) {
                Class<?> classParam = method.getParameterTypes()[0];
                if (classParam.isAssignableFrom(signalClass)) {
                    if (fInboundSignalBlacklist.containsKey(entry.getKey())) {
                        /* Check if any of the ignore rule apply to the signal */
                        boolean isBlackListed = fInboundSignalBlacklist.get(checkNotNull(entry.getKey())).stream()
                                .anyMatch( x ->  x.isAssignableFrom(classParam));
                        if (isBlackListed) {
                            continue;
                        }
                    }
                    /* No rules apply add it */
                    matchingMethods.add(method);
                }
            }
            if (!matchingMethods.isEmpty()) {
                targets.put(entry.getKey(), matchingMethods);
            }
        }

        // Call the signal handlers
        for (Map.Entry<Object, List<Method>> entry : targets.entrySet()) {
            for (Method method : entry.getValue()) {
                try {
                    method.invoke(entry.getKey(), new Object[] { signal });
                    if (TmfCoreTracer.isSignalTraced()) {
                        Object key = entry.getKey();
                        String hash = String.format("%1$08X", entry.getKey().hashCode()); //$NON-NLS-1$
                        String target = "[" + hash + "] " + key.getClass().getSimpleName() + ":" + method.getName();   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
                        TmfCoreTracer.traceSignal(signal, target);
                    }
                } catch (IllegalArgumentException e) {
                    Activator.logError("Exception handling signal " + signal + " in method " + method, e); //$NON-NLS-1$ //$NON-NLS-2$
                } catch (IllegalAccessException e) {
                    Activator.logError("Exception handling signal " + signal + " in method " + method, e); //$NON-NLS-1$ //$NON-NLS-2$
                } catch (InvocationTargetException e) {
                    Activator.logError("Exception handling signal " + signal + " in method " + method, e); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }

        if (TmfCoreTracer.isSignalTraced()) {
            TmfCoreTracer.traceSignal(signal, "(end)"); //$NON-NLS-1$
        }
    }

}
