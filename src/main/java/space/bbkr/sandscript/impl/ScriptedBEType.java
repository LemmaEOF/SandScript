package space.bbkr.sandscript.impl;

import org.sandboxpowered.sandbox.api.Registries;
import org.sandboxpowered.sandbox.api.block.Block;
import org.sandboxpowered.sandbox.api.block.Material;
import org.sandboxpowered.sandbox.api.block.entity.BlockEntity;
import org.sandboxpowered.sandbox.api.block.entity.BlockEntity;
import org.sandboxpowered.sandbox.api.util.Identity;
import space.bbkr.sandscript.ScriptManager;
import space.bbkr.sandscript.helper.TextHelper;
import space.bbkr.sandscript.util.ScriptIdentity;
import space.bbkr.sandscript.util.ScriptLogger;
import space.bbkr.sandscript.util.ScriptStorage;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class ScriptedBEType {
	private Identity id;
	private String script;
	private ScriptEngine engine;
	private Invocable runner;
	private ScriptLogger logger;

	public ScriptedBEType(Identity id) {
		this.id = id;
		this.script = ScriptManager.INSTANCE.getRawScript(id);
		this.engine = init();
		if (this.engine instanceof Invocable) {
			this.runner = (Invocable)engine;
		} else throw new IllegalArgumentException("Script engine " + engine.getFactory().getEngineName() + " is not invocable! This cannot be used for making block entities!");
		this.logger = new ScriptLogger(id.getNamespace());
	}

	public BlockEntity build() {
		try {
			Object result = runner.invokeFunction("build");
			if (result instanceof BlockEntity) return (BlockEntity)result;
			else throw new IllegalArgumentException("Bad return value for build in " + id.toString() + ": must be a block entity");
		} catch (ScriptException e) {
			logger.error("Cannot calculate build for %s: %s", id.toString(), e.getMessage());
			return null;
		} catch (NoSuchMethodException e) {
			logger.debug("No function found for build in %s, returning null", id.toString());
			return null;
		}
	}

	public Block[] getValidBlocks() {
		List<Block> ret;
		try {
			Object result = runner.invokeFunction("getValidBlocks");
			if (result instanceof Block[]) ret = Arrays.asList((Block[])result);
			else if (result instanceof String[]) {
				ret = new ArrayList<>();
				for (String block : (String[])result) {
					ret.add(Registries.BLOCK.get(ScriptIdentity.of(block)).get());
				}
			} else throw new IllegalArgumentException("Bad return value for getValidBlocks in " + id.toString() + ": must be an array of Blocks or Strings");
		} catch (ScriptException e) {
			logger.error("Cannot calculate getValidBlocks for %s: %s", id.toString(), e.getMessage());
			return new Block[]{};
		} catch (NoSuchMethodException e) {
			logger.debug("No function found for getValidBlocks in %s, returning empty list", id.toString());
			return new Block[]{};
		}
		return ret.toArray(new Block[]{});
	}

	public <T extends BlockEntity> BlockEntity.Type<T> getType() {
		Supplier supplier = this::build;
		Block[] validBlocks = getValidBlocks();
		return BlockEntity.Type.of(supplier, validBlocks);
	}

	private ScriptEngine init() {
		String extension = id.getPath().substring(id.getPath().lastIndexOf('.') + 1);
		ScriptEngine engine = ScriptManager.INSTANCE.SCRIPT_MANAGER.getEngineByExtension(extension);
		if (engine == null) {
			logger.error("Could not find engine for extension: " + extension);
			return null;
		}
		try {
			ScriptContext ctx = engine.getContext();
			ctx.setAttribute("storage", ScriptStorage.of(id.getNamespace()), ScriptContext.ENGINE_SCOPE);
			ctx.setAttribute("log", new ScriptLogger(id.getNamespace()), ScriptContext.ENGINE_SCOPE);
			ctx.setAttribute("Text", TextHelper.INSTANCE, ScriptContext.ENGINE_SCOPE);
			engine.eval(script);
			return engine;
		} catch (ScriptException e) {
			logger.error("Error initializing block entity script %s: %s", id.toString(), e.getMessage());
			return null;
		}
	}

}
