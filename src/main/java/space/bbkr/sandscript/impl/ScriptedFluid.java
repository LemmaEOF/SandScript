package space.bbkr.sandscript.impl;

import org.sandboxpowered.sandbox.api.Registries;
import org.sandboxpowered.sandbox.api.block.Block;
import org.sandboxpowered.sandbox.api.block.FluidBlock;
import org.sandboxpowered.sandbox.api.block.Block;
import org.sandboxpowered.sandbox.api.block.Material;
import org.sandboxpowered.sandbox.api.fluid.Fluid;
import org.sandboxpowered.sandbox.api.fluid.Fluid;
import org.sandboxpowered.sandbox.api.item.BucketItem;
import org.sandboxpowered.sandbox.api.item.Item;
import org.sandboxpowered.sandbox.api.item.Item;
import org.sandboxpowered.sandbox.api.state.BlockState;
import org.sandboxpowered.sandbox.api.state.FluidState;
import org.sandboxpowered.sandbox.api.state.Properties;
import org.sandboxpowered.sandbox.api.state.StateFactory;
import org.sandboxpowered.sandbox.api.util.Identity;
import space.bbkr.sandscript.ScriptManager;
import space.bbkr.sandscript.util.ScriptLogger;
import space.bbkr.sandscript.util.ScriptStorage;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.function.Supplier;

public class ScriptedFluid extends SimpleFluid {
	private Identity id;

	private Identity scriptId;
	private String script;
	protected ScriptEngine engine;
	protected Invocable runner;
	private ScriptLogger logger;

	public ScriptedFluid(Identity id, Identity scriptId, boolean isBase) {
		super(id, false, isBase);
		this.id = id;
		this.scriptId = scriptId;
		this.script = ScriptManager.INSTANCE.getRawScript(scriptId);
		this.engine = init();
		if (this.engine instanceof Invocable) {
			this.runner = (Invocable)engine;
		} else throw new IllegalArgumentException("Script engine " + engine.getFactory().getEngineName() + " is not invocable! This cannot be used for making fluids!");
		this.logger = new ScriptLogger(id.getNamespace());
	}

	Supplier<Fluid> getFlowing() {
		return () -> new Flowing(id, scriptId, this);
	}

	@Override
	public boolean isInfinite() {
		try {
			Object ret = runner.invokeFunction("isInfinite");
			if (ret instanceof Boolean) return (Boolean)ret;
			else throw new IllegalArgumentException("Bad return value for isInfinite in " + scriptId.toString() + ": must be a boolean");
		} catch (ScriptException e) {
			logger.error("Cannot calculate isInfinite for %s: %s", id.toString(), e.getMessage());
			return false;
		} catch (NoSuchMethodException e) {
			logger.debug("No function found for isInfinite in %s, returning false", id.toString());
			return false;
		}
	}

	private ScriptEngine init() {
		String extension = scriptId.getPath().substring(scriptId.getPath().lastIndexOf('.') + 1);
		ScriptEngine engine = ScriptManager.INSTANCE.SCRIPT_MANAGER.getEngineByExtension(extension);
		if (engine == null) {
			logger.error("Could not find engine for extension: " + extension);
			return null;
		}
		try {
			ScriptContext ctx = engine.getContext();
			ctx.setAttribute("storage", ScriptStorage.of(scriptId.getNamespace()), ScriptContext.ENGINE_SCOPE);
			ctx.setAttribute("log", new ScriptLogger(scriptId.getNamespace()), ScriptContext.ENGINE_SCOPE);
			engine.eval(script);
			return engine;
		} catch (ScriptException e) {
			logger.error("Error initializing fluid script %s: %s", scriptId.toString(), e.getMessage());
			return null;
		}
	}

	public static class Flowing extends ScriptedFluid {
		private Fluid parent;
		public Flowing(Identity id, Identity scriptId, Fluid parent) {
			super(id, scriptId, false);
			this.parent = parent;
		}

		@Override
		public boolean isStill(FluidState state) {
			return false;
		}

		@Override
		public boolean isInfinite() {
			return parent.isInfinite();
		}

		@Override
		public void appendProperties(StateFactory.Builder<Fluid, FluidState> builder) {
			super.appendProperties(builder);
			builder.add(Properties.FLUID_LEVEL);
		}
	}
}
