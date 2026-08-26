package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

final class EspeakLibrary {

    private static volatile EspeakLibrary INSTANCE;

    private final MethodHandle espeakInitialize;
    private final MethodHandle espeakSetVoiceByName;
    private final MethodHandle espeakTextToPhonemes;
    private final MethodHandle espeakTerminate;

    private volatile boolean initialized;
    private volatile String currentVoice;

    private EspeakLibrary(SymbolLookup lookup) {
        Linker linker = Linker.nativeLinker();

        espeakInitialize = downcall(linker, lookup, "espeak_Initialize",
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));

        espeakSetVoiceByName = downcall(linker, lookup, "espeak_SetVoiceByName",
                FunctionDescriptor.of(JAVA_INT, ADDRESS));

        espeakTextToPhonemes = downcall(linker, lookup, "espeak_TextToPhonemes",
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

        espeakTerminate = downcall(linker, lookup, "espeak_Terminate",
                FunctionDescriptor.of(JAVA_INT));
    }

    static EspeakLibrary load(Path libraryPath, Path dataPath) {
        if (INSTANCE != null) { return INSTANCE; }
        synchronized (EspeakLibrary.class) {
            if (INSTANCE != null) { return INSTANCE; }

            Objects.requireNonNull(libraryPath, "libraryPath");
            Objects.requireNonNull(dataPath, "dataPath");

            SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath, Arena.global());
            EspeakLibrary lib = new EspeakLibrary(lookup);
            lib.init(dataPath);
            INSTANCE = lib;
            return INSTANCE;
        }
    }

    private synchronized void init(Path dataPath) {
        if (initialized) { return; }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathSeg = arena.allocateFrom(dataPath.toString());
            int result = (int) espeakInitialize.invokeExact(
                    (int) 1, // AUDIO_OUTPUT_RETRIEVAL
                    (int) 0, // buflength (default)
                    pathSeg,
                    (int) 0  // options
            );
            if (result < 0) {
                throw new SherpaException("espeak_Initialize failed with code " + result);
            }
            initialized = true;
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable t) {
            throw new SherpaException("espeak_Initialize failed", t);
        }
    }

    synchronized String textToPhonemes(String text, String voice) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(voice, "voice");

        if (text.isEmpty()) {
            return "";
        }

        setVoice(voice);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment textBuf = arena.allocateFrom(text);
            MemorySegment textPtrPtr = arena.allocate(ADDRESS);
            textPtrPtr.set(ADDRESS, 0, textBuf);

            StringBuilder result = new StringBuilder();
            int phonemeMode = 0x02; // IPA output

            while (true) {
                MemorySegment phonemePtr = (MemorySegment) espeakTextToPhonemes.invokeExact(
                        textPtrPtr, (int) 1, phonemeMode);

                if (phonemePtr.equals(MemorySegment.NULL)) {
                    break;
                }

                String phonemes = phonemePtr.reinterpret(4096).getString(0);
                if (phonemes.isEmpty()) {
                    break;
                }

                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(phonemes);

                MemorySegment currentTextPtr = textPtrPtr.get(ADDRESS, 0);
                if (currentTextPtr.equals(MemorySegment.NULL)) {
                    break;
                }

                MemorySegment remaining = currentTextPtr.reinterpret(text.length() + 1L);
                byte firstByte = remaining.get(ValueLayout.JAVA_BYTE, 0);
                if (firstByte == 0) {
                    break;
                }
            }

            return result.toString().strip();
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable t) {
            throw new SherpaException("espeak_TextToPhonemes failed", t);
        }
    }

    private void setVoice(String voice) {
        if (voice.equals(currentVoice)) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment voiceSeg = arena.allocateFrom(voice);
            int result = (int) espeakSetVoiceByName.invokeExact(voiceSeg);
            if (result != 0) {
                throw new SherpaException("espeak_SetVoiceByName failed for voice '" + voice + "': error " + result);
            }
            currentVoice = voice;
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable t) {
            throw new SherpaException("espeak_SetVoiceByName failed", t);
        }
    }

    private static MethodHandle downcall(Linker linker, SymbolLookup lookup,
                                          String name, FunctionDescriptor descriptor) {
        var symbol = lookup.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found in espeak-ng: " + name));
        return linker.downcallHandle(symbol, descriptor);
    }
}
