package org.nvotes.libmix.mpservice;

import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.math.BigInteger;
import java.util.function.Supplier;
import com.squareup.jnagmp.Gmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.nvotes.libmix.Util;

public class MPBridge {

	private static boolean useGmp = Util.getEnvBoolean("libmix.gmp");
	private static boolean useExtractor = Util.getEnvBoolean("libmix.extractor");

	private BigInteger dummy = new BigInteger("2");
	private BigInteger modulus = null;

	private boolean recording = false;
	private boolean replaying = false;

	private LinkedList<ModPow2> requests = new LinkedList<ModPow2>();
	private List<BigInteger> answers = null;

	private final static Logger logger = LoggerFactory.getLogger(MPBridge.class);

	private static ThreadLocal<MPBridge> instance = new ThreadLocal<MPBridge>() {
		@Override protected MPBridge initialValue() {
			return new MPBridge();
        }
	};

	public static void init() {
		logger.info("***************************************************");
		logger.info("* MPBridge INIT");
		logger.info("*");
		logger.info("* useGmp: " + useGmp);
		logger.info("* useExtractor: " + useExtractor);
		logger.info("* MPService implementation: " + MPService.toString());
		MPService.init();
		logger.info("***************************************************");
	}

	public static MPBridge i() {
		return instance.get();
	}

	public static void startRecord(String value) {
		i().dummy = new BigInteger(value);
		if(i().requests.size() != 0)	throw new IllegalStateException();
		i().recording = useExtractor;
		i().modulus = null;
	}

	public static void startRecord() {
		startRecord("2");
	}

	public static ModPow2[] stopRecord() {
		i().recording = false;

		return i().requests.toArray(new ModPow2[0]);
	}

	public static void startReplay(BigInteger[] answers_) {
		if(answers_.length != i().requests.size()) throw new IllegalArgumentException(answers_.length + "!=" + i().requests.size());
		i().answers = new LinkedList<BigInteger>(Arrays.asList(answers_));

		i().replaying = true;
	}

	public static void stopReplay() {
		if(i().answers.size() != 0) throw new IllegalStateException();

		i().replaying = false;
	}

	public static void reset() {
		i().requests.clear();
	}

	public static void addModPow(BigInteger base, BigInteger pow, BigInteger mod) {
		MPBridge i = i();
		if(!i.recording) throw new IllegalStateException();
		if(i.modulus == null) {
			i.modulus = mod;
		}
		// sanity check
		else if(!i.modulus.equals(mod)) {
			throw new RuntimeException(i.modulus + "!=" + mod);
		}

		i.requests.add(new ModPow2(base, pow));
	}

	public static LinkedList<ModPow2> getRequests() {
		if(i().recording) throw new IllegalStateException();

		return i().requests;
	}

	public static BigInteger getModPow() {
		if(i().recording) throw new IllegalStateException();

		return i().answers.remove(0);
	}

	public static <T> T par(Supplier<T> f, String v) {
		a();
	 	startRecord(v);
	 	long now = System.currentTimeMillis();
	 	T ret = f.get();
	 	long r = System.currentTimeMillis() - now;
	 	logger.info("Record: [" + r + " ms]");
	 	ModPow2[] reqs = stopRecord();
		b(3);
		if(reqs.length > 0) {
			long now2 = System.currentTimeMillis();
			BigInteger[] answers = MPService.compute(reqs, i().modulus);
			long c = System.currentTimeMillis() - now2;
			startReplay(answers);
			ret = f.get();
			long t = System.currentTimeMillis() - now;
			logger.info("Compute: [" + c + " ms] R+C: [" + (r+c) + " ms] Total: [" + t + " ms]");
			stopReplay();
		}
		reset();

		return ret;
	}

	public static <T> T par(Supplier<T> f) {
		return par(f, "2");
	}

	public static BigInteger modPow(BigInteger base, BigInteger pow, BigInteger mod) {
        MPBridge i = i();
        if(i.recording) {
            total++;
            addModPow(base, pow, mod);
            return i.dummy;
        }
        else if(i.replaying) {
            return getModPow();
        }
        else if(i.replayingDebug) {
            ModPowResult result = getModPowDebug();
            boolean ok = base.equals(result.base()) && pow.equals(result.pow()) && mod.equals(result.mod());
            if(!ok) throw new RuntimeException();

            return result.result();
        }
        else {
            total++;
            if(useGmp) {
                return Gmp.modPowInsecure(base, pow, mod);
            }
            else {
                return base.modPow(pow, mod);
            }
        }
    }

    public static BigInteger getModulus() {
    	return i().modulus;
    }

	public static boolean isRecording() {
		return i().recording;
	}

	public static boolean isReplaying() {
		return i().replaying;
	}

	public static void shutdown() {
		MPService.shutdown();
	}

	/****************************** DEBUG STUFF ****************************/

	private boolean replayingDebug = false;
	private List<ModPowResult> answersDebug = null;

	// tracing vars
	public long before = 0;
	public static long total = 0;
	private long beforeTime = 0;

	public static void startReplayDebug(ModPowResult[] answers_) {
		if(answers_.length != i().requests.size()) throw new IllegalArgumentException(answers_.length + "!=" + i().requests.size());
		i().answersDebug = new LinkedList<ModPowResult>(Arrays.asList(answers_));

		i().replayingDebug = true;
	}

	public static void stopReplayDebug() {
		if(i().answersDebug.size() != 0) throw new IllegalStateException();

		i().replayingDebug = false;
	}

	public static ModPowResult getModPowDebug() {
		if(i().recording) throw new IllegalStateException();

		return i().answersDebug.remove(0);
	}

	public static <T> T parDebug(Supplier<T> f, String v) {
		a();
	 	startRecord(v);
	 	long now = System.currentTimeMillis();
	 	T ret = f.get();
	 	long r = System.currentTimeMillis() - now;
	 	logger.info("R: [" + r + " ms]");
	 	ModPow2[] reqs = stopRecord();
		b(3);
		if(reqs.length > 0) {
			long now2 = System.currentTimeMillis();

			ModPowResult[] answers = MPService.computeDebug(reqs, i().modulus);
			long c = System.currentTimeMillis() - now2;

			startReplayDebug(answers);
			ret = f.get();
			long t = System.currentTimeMillis() - now;
			logger.info("\nC: [" + c + " ms] T: [" + t + " ms] R+C: [" + (r+c) + " ms]");

			stopReplayDebug();
		}
		reset();

		return ret;
	}

	public static <T> T parDebug(Supplier<T> f) {
		return parDebug(f, "2");
	}

	public static void a() {
		i().beforeTime = System.currentTimeMillis();
	}

	public static void b(int trace) {
		MPBridge i = i();
		StackTraceElement[] traces = Thread.currentThread().getStackTrace();
		StackTraceElement caller = traces[trace];
		long diffTime = System.currentTimeMillis() - i.beforeTime;
		logger.info(">>> " + caller.getFileName() + ":" + caller.getLineNumber() + " [" + diffTime + " ms]" + " (" + total + ")");
	}

	public static void b() {
		b(3);
	}
}