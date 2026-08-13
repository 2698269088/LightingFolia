package top.mcocet.lightingFolia.transform;

import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;

/**
 * Java Agent for LightingFolia bytecode patching.
 * 
 * This agent can be loaded at JVM startup via -javaagent flag or dynamically
 * at runtime via ByteBuddy.
 */
public class BridgeAgent {

    private static final Logger LOGGER = Logger.getLogger("LightingFolia-Agent");
    private static Instrumentation instrumentation;

    /**
     * Called when the agent is loaded at JVM startup via -javaagent
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        LOGGER.info("[LightingFolia] Agent loaded via premain");
        init(inst);
    }

    /**
     * Called when the agent is loaded dynamically after JVM startup
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        LOGGER.info("[LightingFolia] Agent loaded via agentmain");
        init(inst);
    }

    private static void init(Instrumentation inst) {
        instrumentation = inst;
        
        // Register the ASM transformer
        BridgeClassTransformer transformer = new BridgeClassTransformer();
        inst.addTransformer(transformer, true);

        LOGGER.info("[LightingFolia] ASM transformer registered. Classes will be transformed on load.");
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
