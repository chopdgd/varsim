<b>VarSim: A high-fidelity simulation validation framework for high-throughput genome sequencing with cancer applications</b>

See http://bioinform.github.io/varsim/ for help, downloads and webapps.


# How to install varsim

```
./build.sh
virtualenv -p python2.7 pyvenv
source pyvenv/bin/activate
pip install -r requirements.txt
```

**NOTE:** VarSim requires that you activate the pyvenv before running!
**NOTE:** Make sure you have bedtools installed in your PATH

# Examples

## Targeted Capture

```
./varsim_multi.py \
  --out_dir varsim_multi13 \
  --reference /references/GRCh37/human_g1k_v37.fasta \
  --regions opt/regions.bed \
  --samples_random 1 \
  --simulator_executable opt/ART/art_bin_MountRainier/art_illumina \
  --simulator_options "-ss HS25 -sam -p -l 150 -f 20 -m 200 -s 10" \
  --total_coverage 400 \
  --force_five_base_encoding \
  --lift_ref \
  --loglevel debug \
  --disable_rand_dgv \
  --sampling_vcf /resources/dbsnp/20170403/All_20170403.vcf.gz \
  --vc_num_snp 3000000 \
  --vc_num_ins 100000 \
  --vc_num_del 100000 \
  --vc_num_mnp 20000 \
  --vc_num_complex 20000 \
  --vc_percent_novel 0.01
  ```
