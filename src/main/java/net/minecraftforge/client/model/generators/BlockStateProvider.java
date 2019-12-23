/*
 * Minecraft Forge
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.client.model.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FourWayBlock;
import net.minecraft.block.LogBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.RotatedPillarBlock;
import net.minecraft.block.SixWayBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapDoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.IDataProvider;
import net.minecraft.state.properties.AttachFace;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.DoorHingeSide;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.state.properties.Half;
import net.minecraft.state.properties.SlabType;
import net.minecraft.state.properties.StairsShape;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.ResourceLocation;

/**
 * Data provider for blockstate files. Extends {@link BlockModelProvider} so that
 * blockstates and their referenced models can be provided in tandem.
 */
public abstract class BlockStateProvider extends BlockModelProvider {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();

    @VisibleForTesting
    protected final Map<Block, IGeneratedBlockstate> registeredBlocks = new LinkedHashMap<>();

    public BlockStateProvider(DataGenerator gen, String modid, ExistingFileHelper exFileHelper) {
        super(gen, modid, exFileHelper);
    }

    @Override
    protected final void registerModels() {
        registeredBlocks.clear();
        registerStatesAndModels();
        for (Map.Entry<Block, IGeneratedBlockstate> entry : registeredBlocks.entrySet()) {
            saveBlockState(entry.getValue().toJson(), entry.getKey());
        }
    }

    protected abstract void registerStatesAndModels();

    protected VariantBlockStateBuilder getVariantBuilder(Block b) {
        if (registeredBlocks.containsKey(b)) {
            IGeneratedBlockstate old = registeredBlocks.get(b);
            Preconditions.checkState(old instanceof VariantBlockStateBuilder);
            return (VariantBlockStateBuilder) old;
        } else {
            VariantBlockStateBuilder ret = new VariantBlockStateBuilder(b);
            registeredBlocks.put(b, ret);
            return ret;
        }
    }

    protected MultiPartBlockStateBuilder getMultipartBuilder(Block b) {
        if (registeredBlocks.containsKey(b)) {
            IGeneratedBlockstate old = registeredBlocks.get(b);
            Preconditions.checkState(old instanceof MultiPartBlockStateBuilder);
            return (MultiPartBlockStateBuilder) old;
        } else {
            MultiPartBlockStateBuilder ret = new MultiPartBlockStateBuilder(b);
            registeredBlocks.put(b, ret);
            return ret;
        }
    }

    private String name(Block block) {
        return block.getRegistryName().getPath();
    }

    protected ResourceLocation blockTexture(Block block) {
        ResourceLocation name = block.getRegistryName();
        return new ResourceLocation(name.getNamespace(), BLOCK_FOLDER + "/" + name.getPath());
    }

    private ResourceLocation extend(ResourceLocation rl, String suffix) {
        return new ResourceLocation(rl.getNamespace(), rl.getPath() + suffix);
    }

    protected ModelFile cubeAll(Block block) {
        return cubeAll(name(block), blockTexture(block));
    }

    protected void simpleBlock(Block block) {
        simpleBlock(block, cubeAll(block));
    }

    protected void simpleBlock(Block block, Function<ModelFile, ConfiguredModel[]> expander) {
        simpleBlock(block, expander.apply(cubeAll(block)));
    }

    protected void simpleBlock(Block block, ModelFile model) {
        simpleBlock(block, new ConfiguredModel(model));
    }

    protected void simpleBlock(Block block, ConfiguredModel... models) {
        getVariantBuilder(block)
            .partialState().setModels(models);
    }

    protected void axisBlock(RotatedPillarBlock block) {
        axisBlock(block, blockTexture(block));
    }

    protected void logBlock(LogBlock block) {
        axisBlock(block, blockTexture(block), extend(blockTexture(block), "_top"));
    }

    protected void axisBlock(RotatedPillarBlock block, ResourceLocation baseName) {
        axisBlock(block, extend(baseName, "_side"), extend(baseName, "_end"));
    }

    protected void axisBlock(RotatedPillarBlock block, ResourceLocation side, ResourceLocation end) {
        axisBlock(block, cubeColumn(name(block), side, end));
    }

    protected void axisBlock(RotatedPillarBlock block, ModelFile model) {
        getVariantBuilder(block)
            .partialState().with(RotatedPillarBlock.AXIS, Axis.Y)
                .modelForState().modelFile(model).addModel()
            .partialState().with(RotatedPillarBlock.AXIS, Axis.Z)
                .modelForState().modelFile(model).rotationX(90).addModel()
            .partialState().with(RotatedPillarBlock.AXIS, Axis.X)
                .modelForState().modelFile(model).rotationX(90).rotationY(90).addModel();
    }

    private static final int DEFAULT_ANGLE_OFFSET = 180;

    protected void horizontalBlock(Block block, ResourceLocation side, ResourceLocation front, ResourceLocation top) {
        horizontalBlock(block, orientable(name(block), side, front, top));
    }

    protected void horizontalBlock(Block block, ModelFile model) {
        horizontalBlock(block, model, DEFAULT_ANGLE_OFFSET);
    }

    protected void horizontalBlock(Block block, ModelFile model, int angleOffset) {
        horizontalBlock(block, $ -> model, angleOffset);
    }

    protected void horizontalBlock(Block block, Function<BlockState, ModelFile> modelFunc) {
        horizontalBlock(block, modelFunc, DEFAULT_ANGLE_OFFSET);
    }

    protected void horizontalBlock(Block block, Function<BlockState, ModelFile> modelFunc, int angleOffset) {
        getVariantBuilder(block)
            .forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(modelFunc.apply(state))
                    .rotationY(((int) state.get(BlockStateProperties.HORIZONTAL_FACING).getHorizontalAngle() + angleOffset) % 360)
                    .build()
            );
    }

    protected void horizontalFaceBlock(Block block, ModelFile model) {
        horizontalFaceBlock(block, model, DEFAULT_ANGLE_OFFSET);
    }

    protected void horizontalFaceBlock(Block block, ModelFile model, int angleOffset) {
        horizontalFaceBlock(block, $ -> model, angleOffset);
    }

    protected void horizontalFaceBlock(Block block, Function<BlockState, ModelFile> modelFunc) {
        horizontalBlock(block, modelFunc, DEFAULT_ANGLE_OFFSET);
    }

    protected void horizontalFaceBlock(Block block, Function<BlockState, ModelFile> modelFunc, int angleOffset) {
        getVariantBuilder(block)
            .forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(modelFunc.apply(state))
                    .rotationX(state.get(BlockStateProperties.FACE).ordinal() * 90)
                    .rotationY((((int) state.get(BlockStateProperties.HORIZONTAL_FACING).getHorizontalAngle() + angleOffset) + (state.get(BlockStateProperties.FACE) == AttachFace.CEILING ? 180 : 0)) % 360)
                    .build()
            );
    }

    protected void directionalBlock(Block block, ModelFile model) {
        directionalBlock(block, model, DEFAULT_ANGLE_OFFSET);
    }

    protected void directionalBlock(Block block, ModelFile model, int angleOffset) {
        directionalBlock(block, $ -> model, angleOffset);
    }

    protected void directionalBlock(Block block, Function<BlockState, ModelFile> modelFunc) {
        directionalBlock(block, modelFunc, DEFAULT_ANGLE_OFFSET);
    }

    protected void directionalBlock(Block block, Function<BlockState, ModelFile> modelFunc, int angleOffset) {
        getVariantBuilder(block)
            .forAllStates(state -> {
                Direction dir = state.get(BlockStateProperties.FACING);
                return ConfiguredModel.builder()
                    .modelFile(modelFunc.apply(state))
                    .rotationX(dir == Direction.DOWN ? 180 : dir.getAxis().isHorizontal() ? 90 : 0)
                    .rotationY(dir.getAxis().isVertical() ? 0 : (((int) dir.getHorizontalAngle()) + angleOffset) % 360)
                    .build();
            });
    }

    protected void stairsBlock(StairsBlock block, ResourceLocation texture) {
        stairsBlock(block, texture, texture, texture);
    }

    protected void stairsBlock(StairsBlock block, String name, ResourceLocation texture) {
        stairsBlock(block, name, texture, texture, texture);
    }

    protected void stairsBlock(StairsBlock block, ResourceLocation side, ResourceLocation bottom, ResourceLocation top) {
        stairsBlockInternal(block, block.getRegistryName().toString(), side, bottom, top);
    }

    protected void stairsBlock(StairsBlock block, String name, ResourceLocation side, ResourceLocation bottom, ResourceLocation top) {
        stairsBlockInternal(block, name + "_stairs", side, bottom, top);
    }

    private void stairsBlockInternal(StairsBlock block, String baseName, ResourceLocation side, ResourceLocation bottom, ResourceLocation top) {
        ModelFile stairs = stairs(baseName, side, bottom, top);
        ModelFile stairsInner = stairsInner(baseName + "_inner", side, bottom, top);
        ModelFile stairsOuter = stairsOuter(baseName + "_outer", side, bottom, top);
        stairsBlock(block, stairs, stairsInner, stairsOuter);
    }

    protected void stairsBlock(StairsBlock block, ModelFile stairs, ModelFile stairsInner, ModelFile stairsOuter) {
        getVariantBuilder(block)
            .forAllStatesExcept(state -> {
               Direction facing = state.get(StairsBlock.FACING);
               Half half = state.get(StairsBlock.HALF);
               StairsShape shape = state.get(StairsBlock.SHAPE);
               int yRot = (int) facing.rotateY().getHorizontalAngle(); // Stairs model is rotated 90 degrees clockwise for some reason
               if (shape == StairsShape.INNER_LEFT || shape == StairsShape.OUTER_LEFT) {
                   yRot += 270; // Left facing stairs are rotated 90 degrees clockwise
               }
               if (shape != StairsShape.STRAIGHT && half == Half.TOP) {
                   yRot += 90; // Top stairs are rotated 90 degrees clockwise
               }
               yRot %= 360;
               boolean uvlock = yRot != 0 || half == Half.TOP; // Don't set uvlock for states that have no rotation
               return ConfiguredModel.builder()
                       .modelFile(shape == StairsShape.STRAIGHT ? stairs : shape == StairsShape.INNER_LEFT || shape == StairsShape.INNER_RIGHT ? stairsInner : stairsOuter)
                       .rotationX(half == Half.BOTTOM ? 0 : 180)
                       .rotationY(yRot)
                       .uvLock(uvlock)
                       .build();
            }, StairsBlock.WATERLOGGED);
    }

    protected void slabBlock(SlabBlock block, ResourceLocation doubleslab, ResourceLocation texture) {
        slabBlock(block, doubleslab, texture, texture, texture);
    }

    protected void slabBlock(SlabBlock block, ResourceLocation doubleslab, ResourceLocation side, ResourceLocation bottom, ResourceLocation top) {
        slabBlock(block, slab(name(block), side, bottom, top), slabTop(name(block) + "_top", side, bottom, top), getExistingFile(doubleslab));
    }

    protected void slabBlock(SlabBlock block, ModelFile bottom, ModelFile top, ModelFile doubleslab) {
        getVariantBuilder(block)
            .partialState().with(SlabBlock.TYPE, SlabType.BOTTOM).addModels(new ConfiguredModel(bottom))
            .partialState().with(SlabBlock.TYPE, SlabType.TOP).addModels(new ConfiguredModel(top))
            .partialState().with(SlabBlock.TYPE, SlabType.DOUBLE).addModels(new ConfiguredModel(doubleslab));
    }

    protected void fourWayBlock(FourWayBlock block, ModelFile post, ModelFile side) {
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block)
                .part().modelFile(post).addModel().end();
        fourWayMultipart(builder, side);
    }

    protected void fourWayMultipart(MultiPartBlockStateBuilder builder, ModelFile side) {
        SixWayBlock.FACING_TO_PROPERTY_MAP.entrySet().forEach(e -> {
            Direction dir = e.getKey();
            if (dir.getAxis().isHorizontal()) {
                builder.part().modelFile(side).rotationY((((int) dir.getHorizontalAngle()) + 180) % 360).uvLock(true).addModel()
                    .condition(e.getValue(), true);
            }
        });
    }

    protected void fenceBlock(FenceBlock block, ResourceLocation texture) {
        String baseName = block.getRegistryName().toString();
        fourWayBlock(block, fencePost(baseName + "_post", texture), fenceSide(baseName + "_side", texture));
    }

    protected void fenceBlock(FenceBlock block, String name, ResourceLocation texture) {
        fourWayBlock(block, fencePost(name + "_fence_post", texture), fenceSide(name + "_fence_side", texture));
    }

    protected void fenceGateBlock(FenceGateBlock block, ResourceLocation texture) {
        fenceGateBlockInternal(block, block.getRegistryName().toString(), texture);
    }

    protected void fenceGateBlock(FenceGateBlock block, String name, ResourceLocation texture) {
        fenceGateBlockInternal(block, name + "_fence_gate", texture);
    }

    private void fenceGateBlockInternal(FenceGateBlock block, String baseName, ResourceLocation texture) {
        ModelFile gate = fenceGate(baseName, texture);
        ModelFile gateOpen = fenceGateOpen(baseName + "_open", texture);
        ModelFile gateWall = fenceGateWall(baseName + "_wall", texture);
        ModelFile gateWallOpen = fenceGateWallOpen(baseName + "_wall_open", texture);
        fenceGateBlock(block, gate, gateOpen, gateWall, gateWallOpen);
    }

    protected void fenceGateBlock(FenceGateBlock block, ModelFile gate, ModelFile gateOpen, ModelFile gateWall, ModelFile gateWallOpen) {
        getVariantBuilder(block).forAllStatesExcept(state -> {
            ModelFile model = gate;
            if (state.get(FenceGateBlock.IN_WALL)) {
                model = gateWall;
            }
            if (state.get(FenceGateBlock.OPEN)) {
                model = model == gateWall ? gateWallOpen : gateOpen;
            }
            return ConfiguredModel.builder()
                    .modelFile(model)
                    .rotationY((int) state.get(FenceGateBlock.HORIZONTAL_FACING).getHorizontalAngle())
                    .uvLock(true)
                    .build();
        }, FenceGateBlock.POWERED);
    }

    protected void wallBlock(WallBlock block, ResourceLocation texture) {
        wallBlockInternal(block, block.getRegistryName().toString(), texture);
    }

    protected void wallBlock(WallBlock block, String name, ResourceLocation texture) {
        wallBlockInternal(block, name + "_wall", texture);
    }

    private void wallBlockInternal(WallBlock block, String baseName, ResourceLocation texture) {
        wallBlock(block, wallPost(baseName + "_post", texture), wallSide(baseName + "_side", texture));
    }

    protected void wallBlock(WallBlock block, ModelFile post, ModelFile side) {
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block)
                .part().modelFile(post).addModel()
                    .condition(WallBlock.UP, true).end();
        fourWayMultipart(builder, side);
    }

    protected void paneBlock(PaneBlock block, ResourceLocation pane, ResourceLocation edge) {
        paneBlockInternal(block, block.getRegistryName().toString(), pane, edge);
    }

    protected void paneBlock(PaneBlock block, String name, ResourceLocation pane, ResourceLocation edge) {
        paneBlockInternal(block, name + "_pane", pane, edge);
    }

    private void paneBlockInternal(PaneBlock block, String baseName, ResourceLocation pane, ResourceLocation edge) {
        ModelFile post = panePost(baseName + "_post", pane, edge);
        ModelFile side = paneSide(baseName + "_side", pane, edge);
        ModelFile sideAlt = paneSideAlt(baseName + "_side_alt", pane, edge);
        ModelFile noSide = paneNoSide(baseName + "_noside", pane);
        ModelFile noSideAlt = paneNoSideAlt(baseName + "_noside_alt", pane);
        paneBlock(block, post, side, sideAlt, noSide, noSideAlt);
    }

    protected void paneBlock(PaneBlock block, ModelFile post, ModelFile side, ModelFile sideAlt, ModelFile noSide, ModelFile noSideAlt) {
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block)
                .part().modelFile(post).addModel().end();
        SixWayBlock.FACING_TO_PROPERTY_MAP.entrySet().forEach(e -> {
            Direction dir = e.getKey();
            if (dir.getAxis().isHorizontal()) {
                boolean alt = dir == Direction.SOUTH;
                builder.part().modelFile(alt || dir == Direction.WEST ? sideAlt : side).rotationY(dir.getAxis() == Axis.X ? 90 : 0).addModel()
                    .condition(e.getValue(), true).end()
                .part().modelFile(alt || dir == Direction.EAST ? noSideAlt : noSide).rotationY(dir == Direction.WEST ? 270 : dir == Direction.SOUTH ? 90 : 0).addModel()
                    .condition(e.getValue(), false);
            }
        });
    }

    protected void doorBlock(DoorBlock block, ResourceLocation bottom, ResourceLocation top) {
        doorBlockInternal(block, block.getRegistryName().toString(), bottom, top);
    }

    protected void doorBlock(DoorBlock block, String name, ResourceLocation bottom, ResourceLocation top) {
        doorBlockInternal(block, name + "_door", bottom, top);
    }

    private void doorBlockInternal(DoorBlock block, String baseName, ResourceLocation bottom, ResourceLocation top) {
        ModelFile bottomLeft = doorBottomLeft(baseName + "_bottom", bottom, top);
        ModelFile bottomRight = doorBottomRight(baseName + "_bottom_hinge", bottom, top);
        ModelFile topLeft = doorTopLeft(baseName + "_top", bottom, top);
        ModelFile topRight = doorTopRight(baseName + "_top_hinge", bottom, top);
        doorBlock(block, bottomLeft, bottomRight, topLeft, topRight);
    }

    protected void doorBlock(DoorBlock block, ModelFile bottomLeft, ModelFile bottomRight, ModelFile topLeft, ModelFile topRight) {
        getVariantBuilder(block).forAllStatesExcept(state -> {
            int yRot = ((int) state.get(DoorBlock.FACING).getHorizontalAngle()) + 90;
            boolean rh = state.get(DoorBlock.HINGE) == DoorHingeSide.RIGHT;
            boolean open = state.get(DoorBlock.OPEN);
            boolean right = rh ^ open;
            if (open) {
                yRot += 90;
            }
            if (rh && open) {
                yRot += 180;
            }
            yRot %= 360;
            return ConfiguredModel.builder().modelFile(state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? (right ? bottomRight : bottomLeft) : (right ? topRight : topLeft))
                    .rotationY(yRot)
                    .build();
        }, DoorBlock.POWERED);
    }

    protected void trapdoorBlock(TrapDoorBlock block, ResourceLocation texture, boolean orientable) {
        trapdoorBlockInternal(block, block.getRegistryName().toString(), texture, orientable);
    }

    protected void trapdoorBlock(TrapDoorBlock block, String name, ResourceLocation texture, boolean orientable) {
        trapdoorBlockInternal(block, name + "_trapdoor", texture, orientable);
    }

    private void trapdoorBlockInternal(TrapDoorBlock block, String baseName, ResourceLocation texture, boolean orientable) {
        ModelFile bottom = orientable ? trapdoorOrientableBottom(baseName + "_bottom", texture) : trapdoorBottom(baseName + "_bottom", texture);
        ModelFile top = orientable ? trapdoorOrientableTop(baseName + "_top", texture) : trapdoorTop(baseName + "_top", texture);
        ModelFile open = orientable ? trapdoorOrientableOpen(baseName + "_open", texture) : trapdoorOpen(baseName + "_open", texture);
        trapdoorBlock(block, bottom, top, open, orientable);
    }

    protected void trapdoorBlock(TrapDoorBlock block, ModelFile bottom, ModelFile top, ModelFile open, boolean orientable) {
        getVariantBuilder(block).forAllStatesExcept(state -> {
            int xRot = 0;
            int yRot = ((int) state.get(TrapDoorBlock.HORIZONTAL_FACING).getHorizontalAngle()) + 180;
            boolean isOpen = state.get(TrapDoorBlock.OPEN);
            if (orientable && isOpen && state.get(TrapDoorBlock.HALF) == Half.TOP) {
                xRot += 180;
                yRot += 180;
            }
            if (!orientable && !isOpen) {
                yRot = 0;
            }
            yRot %= 360;
            return ConfiguredModel.builder().modelFile(isOpen ? open : state.get(TrapDoorBlock.HALF) == Half.TOP ? top : bottom)
                    .rotationX(xRot)
                    .rotationY(yRot)
                    .build();
        }, TrapDoorBlock.POWERED, TrapDoorBlock.WATERLOGGED);
    }

    private void saveBlockState(JsonObject stateJson, Block owner) {
        ResourceLocation blockName = Preconditions.checkNotNull(owner.getRegistryName());
        Path mainOutput = generator.getOutputFolder();
        String pathSuffix = "assets/" + blockName.getNamespace() + "/blockstates/" + blockName.getPath() + ".json";
        Path outputPath = mainOutput.resolve(pathSuffix);
        try {
            IDataProvider.save(GSON, cache, stateJson, outputPath);
        } catch (IOException e) {
            LOGGER.error("Couldn't save blockstate to {}", outputPath, e);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "Block States";
    }

    public static class ConfiguredModelList {
        private final List<ConfiguredModel> models;

        private ConfiguredModelList(List<ConfiguredModel> models) {
            Preconditions.checkArgument(!models.isEmpty());
            this.models = models;
        }

        public ConfiguredModelList(ConfiguredModel model) {
            this(ImmutableList.of(model));
        }

        public ConfiguredModelList(ConfiguredModel... models) {
            this(Arrays.asList(models));
        }

        public JsonElement toJSON() {
            if (models.size()==1) {
                return models.get(0).toJSON(false);
            } else {
                JsonArray ret = new JsonArray();
                for (ConfiguredModel m:models) {
                    ret.add(m.toJSON(true));
                }
                return ret;
            }
        }

        public ConfiguredModelList append(ConfiguredModel... models) {
            return new ConfiguredModelList(ImmutableList.<ConfiguredModel>builder().addAll(this.models).add(models).build());
        }
    }
}
