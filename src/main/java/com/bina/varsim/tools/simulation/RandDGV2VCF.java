package com.bina.varsim.tools.simulation;

import com.bina.varsim.constants.Constant;
import com.bina.varsim.types.*;
import com.bina.varsim.types.variant.Variant;
import com.bina.varsim.types.variant.VariantType;
import com.bina.varsim.util.DGVparser;
import com.bina.varsim.util.SimpleReference;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.util.*;

/**
 * @author johnmu
 */

public class RandDGV2VCF extends RandVCFgenerator {
    static final int SEED_ARG = 333;
    static final int NUM_INS_ARG = 2000;
    static final int NUM_DEL_ARG = 2000;
    static final int NUM_DUP_ARG = 500;
    static final int NUM_INV_ARG = 500;
    static final double NOVEL_RATIO_ARG = 0.01;
    static final int MIN_LEN_ARG = Constant.SVLEN;
    static final int MAX_LEN_ARG = 1000000;
    static final double PROP_HET_ARG = 0.6;
    private final static Logger log = Logger.getLogger(RandDGV2VCF.class.getName());
    @Option(name = "-seed", usage = "Seed for random sampling [" + SEED_ARG + "]")
    static int seed = SEED_ARG;
    @Option(name = "-all", usage = "Output all variants, don't sample")
    boolean outputAll;
    @Option(name = "-num_ins", usage = "Number of insertion SV to sample [" + NUM_INS_ARG + "]")
    int numIns = NUM_INS_ARG;
    @Option(name = "-num_del", usage = "Number of deletion SV to sample [" + NUM_DEL_ARG + "]")
    int numDel = NUM_DEL_ARG;
    @Option(name = "-num_dup", usage = "Number of duplications to sample [" + NUM_DUP_ARG + "]")
    int numDup = NUM_DUP_ARG;
    @Option(name = "-num_inv", usage = "Number of inversions to sample [" + NUM_INV_ARG + "]")
    int numInv = NUM_INV_ARG;
    @Option(name = "-novel", usage = "Average ratio of novel variants[" + NOVEL_RATIO_ARG + "]")
    double ratioNovel = NOVEL_RATIO_ARG;
    @Option(name = "-min_len", usage = "Minimum variant length [" + MIN_LEN_ARG + "], inclusive")
    int minLengthLim = MIN_LEN_ARG;
    @Option(name = "-max_len", usage = "Maximum variant length [" + MAX_LEN_ARG + "], inclusive")
    int maxLengthLim = MAX_LEN_ARG;
    @Option(name = "-ref", usage = "Reference Genome [Required]", metaVar = "file", required = true)
    String referenceFilename;
    @Option(name = "-ins", usage = "Known Insertion Sequences [Required]", metaVar = "file", required = true)
    String insertFilename;
    @Option(name = "-dgv", usage = "DGV database flat file [Required]", metaVar = "file", required = true)
    String dgvFilename;
    @Option(name = "-t", usage = "Gender of individual [MALE]")
    GenderType gender = GenderType.MALE;
    @Option(name = "-prop_het", usage = "Average ratio of novel variants[" + PROP_HET_ARG + "]")
    double propHet = PROP_HET_ARG;
    @Option(name = "-out_vcf", usage = "Output VCF to generate [stdout]")
    String outFilename = null;

    private final Set<VariantType> variantTypesInDGV = EnumSet.of(VariantType.Insertion, VariantType.Deletion,
            VariantType.Tandem_Duplication, VariantType.Inversion);

    int numNovelAdded = 0;

    public RandDGV2VCF() {
        super();
        numNovelAdded = 0;
    }

    public RandDGV2VCF(long seed) {
        super(seed);
        numNovelAdded = 0;
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) throws IOException {
        // TODO Auto-generated method stub
        RandDGV2VCF runner = new RandDGV2VCF();
        runner.run(args);
    }

    byte[] fileToByteArray(final File file) {
        byte[] array = null;

        try {
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                sb.append(line);
            }
            bufferedReader.close();
            array = sb.toString().getBytes("US-ASCII");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return array;
    }

    Map<VariantType, Integer> countVariantsInDGV(final String dgvFilename, final SimpleReference reference,
                                                 final List<Genotypes> selectedGenotypes) {
        // count the number of variants
        log.info("Counting variants and assigning genotypes");

        int totalNumOther = 0;
        int totalNum = 0;
        int totalLines = 0;
        int totalDuplicate = 0;
        int totalOutOfRange = 0;
        DGVparser dgVparser = new DGVparser(dgvFilename, reference, rand);
        Variant prevVar = new Variant(rand);

        final Map<VariantType, Integer> variantCounts = new EnumMap(VariantType.class);
        for (final VariantType variantType : variantTypesInDGV) {
            variantCounts.put(variantType, 0);
        }

        // Read through a first time to generate the counts for sampling without replacement
        while (dgVparser.hasMoreInput()) {
            Variant var = dgVparser.parseLine();
            if (var == null) {
                continue;
            }

            // select genotypes here
            ChrString chr = var.getChr();
            int numberOfAlternativeAlleles = var.getNumberOfAlternativeAlleles();

            Genotypes geno = new Genotypes(chr, gender, numberOfAlternativeAlleles, rand, propHet);
            selectedGenotypes.add(geno);
            totalLines++;

            if (prevVar.getPos() == var.getPos()) {
                // duplicate
                totalDuplicate++;
                continue;
            }

            prevVar = var;

            if (var.maxLen() > maxLengthLim
                    || var.minLen() < minLengthLim) {
                totalOutOfRange++;
                continue;
            }

            int numIters = (geno.geno[0] == geno.geno[1]) ? 1 : 2;
            for (int i = 0; i < numIters; i++) {
                final VariantType variantType = var.getType(geno.geno[i]);
                if (variantCounts.containsKey(variantType)) {
                    variantCounts.put(variantType, variantCounts.get(variantType) + 1);
                } else {
                    totalNumOther++;
                }
            }
            totalNum++;
        }

        for (final Map.Entry<VariantType, Integer> entry : variantCounts.entrySet()) {
            log.info(entry.getKey().name() + entry.getValue());
        }
        log.info("total_num_skipped: " + totalNumOther);
        log.info("total_num: " + totalNum);
        log.info("total_duplicate: " + totalDuplicate);
        log.info("total_out_of_range: " + totalOutOfRange);
        log.info("total_lines: " + totalLines);

        return variantCounts;
    }

    void sampleFromDGV(final String dgvFilename, final SimpleReference reference, final byte[] insertSeq,
                       final List<Genotypes> selectedGenotypes, final Map<VariantType, Integer> variantCounts,
                       final OutputStream outputStream) throws IOException {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream));

        final String VCF_HEADER = "##fileformat=VCFv4.0\n" +
                "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n" +
                "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tsv\n";
        out.write(VCF_HEADER);

        final Map<VariantType, SampleParams> variantParams = new EnumMap(VariantType.class);
        for (final VariantType variantType : variantTypesInDGV) {
            variantParams.put(variantType, new SampleParams());
        }

        final Map<VariantType, Integer> maxCounts = new EnumMap(VariantType.class);
        maxCounts.put(VariantType.Insertion, numIns);
        maxCounts.put(VariantType.Deletion, numDel);
        maxCounts.put(VariantType.Inversion, numInv);
        maxCounts.put(VariantType.Tandem_Duplication, numDup);

        int genoIdx = 0;
        final DGVparser dgVparser = new DGVparser(dgvFilename, reference, rand);
        Variant prevVar = new Variant(rand);

        // Read through and do the sampling
        while (dgVparser.hasMoreInput()) {
            Variant var = dgVparser.parseLine();
            if (var == null) {
                continue;
            }

            Genotypes geno = selectedGenotypes.get(genoIdx);
            genoIdx++;

            if (prevVar.getPos() == var.getPos()) {
                // duplicate
                continue;
            }

            prevVar = new Variant(var);

            if (var.maxLen() > maxLengthLim || var.minLen() < minLengthLim) {
                continue;
            }

            // sample genotypes
            int numIters = (geno.geno[0] == geno.geno[1]) ? 1 : 2;
            for (int i = 0; i < numIters; i++) {
                final VariantType variantType = var.getType(geno.geno[i]);
                if (variantParams.containsKey(variantType)) {
                    geno.geno[i] = sampleGenotype(geno.geno[i], variantParams.get(variantType),
                            maxCounts.get(variantType), variantCounts.get(variantType), outputAll);
                }
            }

            // write out variant
            if (geno.isNonRef()) {
                randOutputVcfRecord(out, var, reference, insertSeq, ratioNovel, geno);
            }
        }

        out.close();
    }

    // outputs VCF record with random phase
    void randOutputVcfRecord(BufferedWriter bw, Variant var,
                             SimpleReference ref, byte[] insertSeq, double ratioNovel,
                             Genotypes geno) throws IOException {

        ChrString chr = var.getChr();

        // determine whether this one is novel
        double randNum = rand.nextDouble();
        if (randNum <= ratioNovel) {
            // make the variant novel, simply modify it
            // TODO maybe modifying it is bad

            int chrLen = ref.getRefLen(chr);
            int buffer = Math.max(
                    100000,
                    Math.max(var.maxLen(geno.geno[0]),
                            var.maxLen(geno.geno[1]))
            );
            int startVal = Math.min(buffer, Math.max(chrLen - buffer, 0));
            int endVal = Math.max(chrLen - buffer, Math.min(buffer, chrLen));

            int timeOut = 0;
            int newPos = rand.nextInt(endVal - startVal + 1) + startVal + 1;
            while (!var.setNovelPosition(newPos, ref)) {
                if (timeOut > 100) {
                    log.warn("Error, cannot set novel position: " + (endVal - startVal + 1));
                    log.warn(var.getReferenceAlleleLength());
                    log.warn(var);
                    //System.exit(1);
                }

                log.info(timeOut + " : " + newPos + " : " + var.getReferenceAlleleLength());

                newPos = rand.nextInt(endVal - startVal + 1) + startVal + 1;
                timeOut++;
            }

            numNovelAdded++;
            var.setVarID("Novel_" + numNovelAdded);
        }

        // this is ok if both the same genotype
        // the second call will return
        fillInSeq(var, insertSeq, geno.geno[0]);
        fillInSeq(var, insertSeq, geno.geno[1]);

        outputVcfRecord(bw, var, geno.geno[0], geno.geno[1]);
    }


    public void run(String[] args) throws IOException {
        String VERSION = "VarSim " + getClass().getPackage().getImplementationVersion();
        String usage = "Outputs VCF to stdout. Randomly samples variants from DGV flat file.\n";

        CmdLineParser parser = new CmdLineParser(this);

        // if you have a wider console, you could increase the value;
        // here 80 is also the default
        parser.setUsageWidth(80);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(VERSION);
            System.err.println(e.getMessage());
            System.err.println("java -jar randdgv2vcf.jar [options...]");
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println(usage);
            return;
        }

        if (ratioNovel > 1 || ratioNovel < 0) {
            log.fatal("Novel ratio out of range [0,1]");
            System.exit(1);
        }

        rand = new Random(seed);

        log.info("Reading reference");
        final SimpleReference reference = new SimpleReference(referenceFilename);

        // read in the insert sequences
        log.info("Reading insert sequences");
        final byte[] insertSeq = fileToByteArray(new File(insertFilename));

        final List<Genotypes> selectedGenotypes = new ArrayList<>();
        final Map<VariantType, Integer> variantCounts = countVariantsInDGV(dgvFilename, reference, selectedGenotypes);

        final OutputStream outputStream = (outFilename != null) ? new FileOutputStream(outFilename) : System.out;
        sampleFromDGV(dgvFilename, reference, insertSeq, selectedGenotypes, variantCounts, outputStream);
    }

}
