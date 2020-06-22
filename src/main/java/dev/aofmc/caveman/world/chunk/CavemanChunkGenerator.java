package dev.aofmc.caveman.world.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.aofmc.caveman.Caveman;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.JigsawJunction;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Util;
import net.minecraft.util.math.*;
import net.minecraft.util.math.noise.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.feature.StructureFeature;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public class CavemanChunkGenerator extends ChunkGenerator {
    public static final Codec<CavemanChunkGenerator> CODEC = RecordCodecBuilder
        .create(instance -> instance
            .group(
                BiomeSource.field_24713.fieldOf("biome_source")
                    .forGetter(generator -> generator.biomeSource),
                Codec.LONG.fieldOf("seed")
                    .stable()
                    .forGetter(generator -> generator.seed)
            )
            .apply(instance, instance.stable(CavemanChunkGenerator::new)));

    private final BiomeSource biomeSource;
    private final long seed;
    private final ChunkGeneratorType chunkGeneratorType;

    private static final float[] field_16649 = Util.make(new float[13824], (fs) -> {
        for(int i = 0; i < 24; ++i) {
            for(int j = 0; j < 24; ++j) {
                for(int k = 0; k < 24; ++k) {
                    fs[i * 24 * 24 + j * 24 + k] = (float)method_16571(j - 12, k - 12, i - 12);
                }
            }
        }

    });
    private static final BlockState AIR;
    private final int height;
    private final int verticalNoiseResolution;
    private final int horizontalNoiseResolution;
    private final int noiseSizeX;
    private final int noiseSizeY;
    private final int noiseSizeZ;
    protected final ChunkRandom random;
    private final OctavePerlinNoiseSampler lowerInterpolatedNoise;
    private final OctavePerlinNoiseSampler upperInterpolatedNoise;
    private final OctavePerlinNoiseSampler interpolationNoise;
    private final NoiseSampler surfaceDepthNoise;
    protected final BlockState defaultBlock;
    protected final BlockState defaultFluid;


    public CavemanChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource, new StructuresConfig(true));
        this.biomeSource = biomeSource;
        this.seed = seed;
        this.chunkGeneratorType = Caveman.CHUNK_GEN_PRESET.getChunkGeneratorType();

        NoiseConfig noiseConfig = chunkGeneratorType.method_28559();

        this.height = noiseConfig.getHeight();
        this.verticalNoiseResolution = noiseConfig.getSizeVertical() * 4;
        this.horizontalNoiseResolution = noiseConfig.getSizeHorizontal() * 4;
        this.defaultBlock = chunkGeneratorType.getDefaultBlock();
        this.defaultFluid = chunkGeneratorType.getDefaultFluid();
        this.noiseSizeX = 16 / this.horizontalNoiseResolution;
        this.noiseSizeY = noiseConfig.getHeight() / this.verticalNoiseResolution;
        this.noiseSizeZ = 16 / this.horizontalNoiseResolution;
        this.random = new ChunkRandom(seed);
        this.lowerInterpolatedNoise = new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-15, 0));
        this.upperInterpolatedNoise = new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-15, 0));
        this.interpolationNoise = new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-7, 0));
        this.surfaceDepthNoise = noiseConfig.hasSimplexSurfaceNoise()
            ? new OctaveSimplexNoiseSampler(this.random, IntStream.rangeClosed(-3, 0))
            : new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-3, 0));
        this.random.consume(2620);
    }

    private double sampleNoise(int x, int y, int z, double d, double e, double f, double g) {
        double h = 0.0D;
        double i = 0.0D;
        double j = 0.0D;
        double k = 1.0D;

        for(int l = 0; l < 16; ++l) {
            double m = OctavePerlinNoiseSampler.maintainPrecision((double)x * d * k);
            double n = OctavePerlinNoiseSampler.maintainPrecision((double)y * e * k);
            double o = OctavePerlinNoiseSampler.maintainPrecision((double)z * d * k);
            double p = e * k;
            PerlinNoiseSampler perlinNoiseSampler = this.lowerInterpolatedNoise.getOctave(l);
            if (perlinNoiseSampler != null) {
                h += perlinNoiseSampler.sample(m, n, o, p, (double)y * p) / k;
            }

            PerlinNoiseSampler perlinNoiseSampler2 = this.upperInterpolatedNoise.getOctave(l);
            if (perlinNoiseSampler2 != null) {
                i += perlinNoiseSampler2.sample(m, n, o, p, (double)y * p) / k;
            }

            if (l < 8) {
                PerlinNoiseSampler perlinNoiseSampler3 = this.interpolationNoise.getOctave(l);
                if (perlinNoiseSampler3 != null) {
                    j += perlinNoiseSampler3.sample(OctavePerlinNoiseSampler.maintainPrecision((double)x * f * k), OctavePerlinNoiseSampler.maintainPrecision((double)y * g * k), OctavePerlinNoiseSampler.maintainPrecision((double)z * f * k), g * k, (double)y * g * k) / k;
                }
            }

            k /= 2.0D;
        }

        return MathHelper.clampedLerp(h / 512.0D, i / 512.0D, (j / 10.0D + 1.0D) / 2.0D);
    }

    protected double[] sampleNoiseColumn(int x, int z) {
        double[] ds = new double[this.noiseSizeY + 1];
        this.sampleNoiseColumn(ds, x, z);
        return ds;
    }

    protected void sampleNoiseColumn(double[] buffer, int x, int z, double d, double e, double f, double g, int i, int j) {
        double[] ds = this.computeNoiseRange(x, z);
        double h = ds[0];
        double k = ds[1];
        double l = this.method_16409();
        double m = this.method_16410();

        for(int n = 0; n < this.getNoiseSizeY(); ++n) {
            double o = this.sampleNoise(x, n, z, d, e, f, g);
            o -= this.computeNoiseFalloff(h, k, n);
            if ((double)n > l) {
                o = MathHelper.clampedLerp(o, j, (n - l) / i);
            } else if ((double)n < m) {
                o = MathHelper.clampedLerp(o, -30.0D, (m - n) / (m - 1.0D));
            }

            buffer[n] = o;
        }

    }

    protected double method_16409() {
        return this.getNoiseSizeY() - 4;
    }

    protected double method_16410() {
        return 0.0D;
    }

    public int getHeight(int x, int z, Heightmap.Type heightmapType) {
        return this.sampleHeightmap(x, z, null, heightmapType.getBlockPredicate());
    }

    public BlockView getColumnSample(int x, int z) {
        BlockState[] blockStates = new BlockState[this.noiseSizeY * this.verticalNoiseResolution];
        this.sampleHeightmap(x, z, blockStates, null);
        return new VerticalBlockSample(blockStates);
    }

    private int sampleHeightmap(int x, int z, @Nullable BlockState[] states, @Nullable Predicate<BlockState> predicate) {
        int i = Math.floorDiv(x, this.horizontalNoiseResolution);
        int j = Math.floorDiv(z, this.horizontalNoiseResolution);
        int k = Math.floorMod(x, this.horizontalNoiseResolution);
        int l = Math.floorMod(z, this.horizontalNoiseResolution);
        double d = (double)k / (double)this.horizontalNoiseResolution;
        double e = (double)l / (double)this.horizontalNoiseResolution;
        double[][] ds = new double[][]{this.sampleNoiseColumn(i, j), this.sampleNoiseColumn(i, j + 1), this.sampleNoiseColumn(i + 1, j), this.sampleNoiseColumn(i + 1, j + 1)};

        for(int m = this.noiseSizeY - 1; m >= 0; --m) {
            double f = ds[0][m];
            double g = ds[1][m];
            double h = ds[2][m];
            double n = ds[3][m];
            double o = ds[0][m + 1];
            double p = ds[1][m + 1];
            double q = ds[2][m + 1];
            double r = ds[3][m + 1];

            for(int s = this.verticalNoiseResolution - 1; s >= 0; --s) {
                double t = (double)s / (double)this.verticalNoiseResolution;
                double u = MathHelper.lerp3(t, d, e, f, o, h, q, g, p, n, r);
                int v = m * this.verticalNoiseResolution + s;
                BlockState blockState = this.getBlockState(u, v);
                if (states != null) {
                    states[v] = blockState;
                }

                if (predicate != null && predicate.test(blockState)) {
                    return v + 1;
                }
            }
        }

        return 0;
    }

    protected BlockState getBlockState(double density, int y) {
        BlockState blockState3;
        if (density > 0.0D) {
            blockState3 = this.defaultBlock;
        } else if (y < this.getSeaLevel()) {
            blockState3 = this.defaultFluid;
        } else {
            blockState3 = AIR;
        }

        return blockState3;
    }

    public int getNoiseSizeY() {
        return this.noiseSizeY + 1;
    }

    @Override
    protected Codec<? extends ChunkGenerator> method_28506() {
        return CODEC;
    }

    @Environment(EnvType.CLIENT)
    @Override
    public ChunkGenerator withSeed(long seed) {
        return new CavemanChunkGenerator(new CavemanBiomeSource(seed), seed);
    }

    public void buildSurface(ChunkRegion chunkRegion, Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.x;
        int j = chunkPos.z;
        ChunkRandom chunkRandom = new ChunkRandom();
        chunkRandom.setSeed(i + j);
        ChunkPos chunkPos2 = chunk.getPos();
        int k = chunkPos2.getStartX();
        int l = chunkPos2.getStartZ();
        double d = 0.0625D;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for(int m = 0; m < 16; ++m) {
            for(int n = 0; n < 16; ++n) {
                int o = k + m;
                int p = l + n;
                int q = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, m, n) + 1;
                double e = this.surfaceDepthNoise.sample(o * d, p * d, d, m * d) * 15.0D;
                chunkRegion
                    .getBiome(mutable.set(k + m, q, l + n))
                    .buildSurface(
                        chunkRandom,
                        chunk,
                        o,
                        p,
                        q,
                        e,
                        this.chunkGeneratorType.getDefaultBlock(),
                        this.chunkGeneratorType.getDefaultFluid(),
                        this.getSeaLevel(),
                        chunkRegion.getSeed()
                    );
            }
        }

        this.buildBedrock(chunk, chunkRandom);
    }

    protected void sampleNoiseColumn(double[] buffer, int x, int z) {
        this.sampleNoiseColumn(buffer, x, z, 684.412D, 684.412D, 8.555150000000001D, 4.2206D, 3, -10);
    }

    //In our chunk generator, depth is the min density and scale is the max density
    protected double[] computeNoiseRange(int x, int z) {

        double scale = 0;
        double depth = 0;

        for (int x1 = -3; x1 <= 3; x1++) {
            for (int z1 = -3; z1 <= 3; z1++) {
                scale += this.biomeSource.getBiomeForNoiseGen(x, 63, z).getScale();
                depth += this.biomeSource.getBiomeForNoiseGen(x, 63, z).getDepth();
            }
        }

        scale /= 49;
        depth /= 49;

        return new double[]{depth, scale};
    }

    protected double computeNoiseFalloff(double depth, double scale, int y) {
        //this lerps the density so most air pockets spawn near y128.
        return MathHelper.lerp(Math.abs(y - (this.getNoiseSizeY() / 2f)) / (this.getNoiseSizeY() / 2f), depth, scale);
    }

    @Override
    public int getSpawnHeight() {
        return 128;
    }

    protected void buildBedrock(Chunk chunk, Random random) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int i = chunk.getPos().getStartX();
        int j = chunk.getPos().getStartZ();
        int k = this.chunkGeneratorType.getBedrockFloorY();
        int l = this.height - 1 - this.chunkGeneratorType.getBedrockCeilingY();
        Iterator var9 = BlockPos.iterate(i, 0, j, i + 15, 0, j + 15).iterator();

        while(true) {
            BlockPos blockPos;
            int n;
            do {
                if (!var9.hasNext()) {
                    return;
                }

                blockPos = (BlockPos)var9.next();
                if (l > 0) {
                    for(n = l; n >= l - 4; --n) {
                        if (n >= l - random.nextInt(5)) {
                            chunk.setBlockState(mutable.set(blockPos.getX(), n, blockPos.getZ()), Blocks.BEDROCK.getDefaultState(), false);
                        }
                    }
                }
            } while(k >= 256);

            for(n = k + 4; n >= k; --n) {
                if (n <= k + random.nextInt(5)) {
                    chunk.setBlockState(mutable.set(blockPos.getX(), n, blockPos.getZ()), Blocks.BEDROCK.getDefaultState(), false);
                }
            }
        }
    }

    @Override
    public void populateNoise(WorldAccess world, StructureAccessor accessor, Chunk chunk) {
        int seaLevel = this.getSeaLevel();
        ObjectList<StructurePiece> objectList = new ObjectArrayList(10);
        ObjectList<JigsawJunction> objectList2 = new ObjectArrayList(32);
        ChunkPos chunkPos = chunk.getPos();
        int j = chunkPos.x;
        int k = chunkPos.z;
        int l = j << 4;
        int m = k << 4;

        for (StructureFeature<?> feature : StructureFeature.field_24861) {
            accessor
                .getStructuresWithChildren(ChunkSectionPos.from(chunkPos, 0), feature)
                .forEach(start -> {
                    StructurePiece structurePiece = null;
                    for (StructurePiece childPiece : start.getChildren()) {
                        if (childPiece.intersectsChunk(chunkPos, 12)) {
                            structurePiece = childPiece;
                            break;
                        }
                    }
                    if (structurePiece == null) {
                        return;
                    }

                    if (structurePiece instanceof PoolStructurePiece) {
                        PoolStructurePiece poolStructurePiece = (PoolStructurePiece) structurePiece;
                        StructurePool.Projection projection = poolStructurePiece.getPoolElement().getProjection();
                        if (projection == StructurePool.Projection.RIGID) {
                            objectList.add(poolStructurePiece);
                        }

                        for (JigsawJunction jigsawJunction : poolStructurePiece.getJunctions()) {
                            int lx = jigsawJunction.getSourceX();
                            int mx = jigsawJunction.getSourceZ();
                            if (lx > l - 12 && mx > m - 12 && lx < l + 15 + 12 && mx < m + 15 + 12) {
                                objectList2.add(jigsawJunction);
                            }
                        }
                    } else {
                        objectList.add(structurePiece);
                    }
            });
        }

        double[][][] ds = new double[2][this.noiseSizeZ + 1][this.noiseSizeY + 1];

        for(int q = 0; q < this.noiseSizeZ + 1; ++q) {
            ds[0][q] = new double[this.noiseSizeY + 1];
            this.sampleNoiseColumn(ds[0][q], j * this.noiseSizeX, k * this.noiseSizeZ + q);
            ds[1][q] = new double[this.noiseSizeY + 1];
        }

        ProtoChunk protoChunk = (ProtoChunk)chunk;
        Heightmap heightmap = protoChunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap heightmap2 = protoChunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        ObjectListIterator<StructurePiece> objectListIterator = objectList.iterator();
        ObjectListIterator<JigsawJunction> objectListIterator2 = objectList2.iterator();

        for(int r = 0; r < this.noiseSizeX; ++r) {
            int t;
            for(t = 0; t < this.noiseSizeZ + 1; ++t) {
                this.sampleNoiseColumn(ds[1][t], j * this.noiseSizeX + r + 1, k * this.noiseSizeZ + t);
            }

            for(t = 0; t < this.noiseSizeZ; ++t) {
                ChunkSection chunkSection = protoChunk.getSection(15);
                chunkSection.lock();

                for(int u = this.noiseSizeY - 1; u >= 0; --u) {
                    double d = ds[0][t][u];
                    double e = ds[0][t + 1][u];
                    double f = ds[1][t][u];
                    double g = ds[1][t + 1][u];
                    double h = ds[0][t][u + 1];
                    double v = ds[0][t + 1][u + 1];
                    double w = ds[1][t][u + 1];
                    double x = ds[1][t + 1][u + 1];

                    for(int y = this.verticalNoiseResolution - 1; y >= 0; --y) {
                        int z = u * this.verticalNoiseResolution + y;
                        int aa = z & 15;
                        int ab = z >> 4;
                        if (chunkSection.getYOffset() >> 4 != ab) {
                            chunkSection.unlock();
                            chunkSection = protoChunk.getSection(ab);
                            chunkSection.lock();
                        }

                        double ac = (double)y / (double)this.verticalNoiseResolution;
                        double ad = MathHelper.lerp(ac, d, h);
                        double ae = MathHelper.lerp(ac, f, w);
                        double af = MathHelper.lerp(ac, e, v);
                        double ag = MathHelper.lerp(ac, g, x);

                        for(int ah = 0; ah < this.horizontalNoiseResolution; ++ah) {
                            int ai = l + r * this.horizontalNoiseResolution + ah;
                            int aj = ai & 15;
                            double ak = (double)ah / (double)this.horizontalNoiseResolution;
                            double al = MathHelper.lerp(ak, ad, ae);
                            double am = MathHelper.lerp(ak, af, ag);

                            for(int an = 0; an < this.horizontalNoiseResolution; ++an) {
                                int ao = m + t * this.horizontalNoiseResolution + an;
                                int ap = ao & 15;
                                double aq = (double)an / (double)this.horizontalNoiseResolution;
                                double ar = MathHelper.lerp(aq, al, am);
                                double as = MathHelper.clamp(ar / 200.0D, -1.0D, 1.0D);

                                int ax;
                                int ay;
                                int av;
                                for(as = as / 2.0D - as * as * as / 24.0D; objectListIterator.hasNext(); as += method_16572(ax, ay, av) * 0.8D) {
                                    PoolStructurePiece poolStructurePiece2 = (PoolStructurePiece)objectListIterator.next();
                                    BlockBox blockBox = poolStructurePiece2.getBoundingBox();
                                    ax = Math.max(0, Math.max(blockBox.minX - ai, ai - blockBox.maxX));
                                    ay = z - (blockBox.minY + poolStructurePiece2.getGroundLevelDelta());
                                    av = Math.max(0, Math.max(blockBox.minZ - ao, ao - blockBox.maxZ));
                                }

                                objectListIterator.back(objectList.size());

                                while(objectListIterator2.hasNext()) {
                                    JigsawJunction jigsawJunction2 = (JigsawJunction)objectListIterator2.next();
                                    int aw = ai - jigsawJunction2.getSourceX();
                                    ax = z - jigsawJunction2.getSourceGroundY();
                                    ay = ao - jigsawJunction2.getSourceZ();
                                    as += method_16572(aw, ax, ay) * 0.4D;
                                }

                                objectListIterator2.back(objectList2.size());
                                BlockState blockState3;
                                if (as > 0.0D) {
                                    blockState3 = this.defaultBlock;
                                } else if (z < seaLevel) {
                                    blockState3 = this.defaultFluid;
                                } else {
                                    blockState3 = AIR;
                                }

                                if (blockState3 != AIR) {
                                    if (blockState3.getLuminance() != 0) {
                                        mutable.set(ai, z, ao);
                                        protoChunk.addLightSource(mutable);
                                    }

                                    chunkSection.setBlockState(aj, aa, ap, blockState3, false);
                                    heightmap.trackUpdate(aj, z, ap, blockState3);
                                    heightmap2.trackUpdate(aj, z, ap, blockState3);
                                }
                            }
                        }
                    }
                }

                chunkSection.unlock();
            }

            double[][] es = ds[0];
            ds[0] = ds[1];
            ds[1] = es;
        }

    }

    private static double method_16572(int i, int j, int k) {
        int l = i + 12;
        int m = j + 12;
        int n = k + 12;
        if (l >= 0 && l < 24) {
            if (m >= 0 && m < 24) {
                return n >= 0 && n < 24 ? (double)field_16649[n * 24 * 24 + l * 24 + m] : 0.0D;
            } else {
                return 0.0D;
            }
        } else {
            return 0.0D;
        }
    }

    private static double method_16571(int i, int j, int k) {
        double d = i * i + k * k;
        double e = j + 0.5D;
        double f = e * e;
        double g = Math.pow(2.718281828459045D, -(f / 16.0D + d / 16.0D));
        double h = -e * MathHelper.fastInverseSqrt(f / 2.0D + d / 2.0D) / 2.0D;
        return h * g;
    }

    static {
        AIR = Blocks.AIR.getDefaultState();
    }
}
