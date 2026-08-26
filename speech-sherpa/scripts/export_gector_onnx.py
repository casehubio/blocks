#!/usr/bin/env python3
"""Export gotutiyan/gector models to ONNX format for casehub-blocks-speech-sherpa.

Usage:
    python export_gector_onnx.py --model gector-deberta-base-5k --output ./gector-base
    python export_gector_onnx.py --model gector-deberta-large-5k --output ./gector-large

Outputs:
    <output>/model.onnx         — DeBERTa encoder with token classification head
    <output>/labels.txt         — tag vocabulary (one tag per line)
    <output>/spiece.model       — SentencePiece tokenizer model
    <output>/verb-form-vocab.txt — verb inflection dictionary (tab-separated)

Requirements:
    pip install torch transformers[sentencepiece] onnx onnxruntime
"""

import argparse
import shutil
import sys
from pathlib import Path

import torch
from transformers import AutoTokenizer, AutoModelForTokenClassification


MODELS = {
    "gector-deberta-base-5k": "gotutiyan/gector-deberta-base-5k",
    "gector-deberta-large-5k": "gotutiyan/gector-deberta-large-5k",
}


def export(model_name: str, output_dir: Path):
    hf_name = MODELS.get(model_name)
    if not hf_name:
        print(f"Unknown model: {model_name}. Choose from: {list(MODELS.keys())}")
        sys.exit(1)

    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading {hf_name}...")
    tokenizer = AutoTokenizer.from_pretrained(hf_name)
    model = AutoModelForTokenClassification.from_pretrained(hf_name)
    model.eval()

    # Export labels.txt
    labels = [model.config.id2label[i] for i in range(model.config.num_labels)]
    labels_path = output_dir / "labels.txt"
    labels_path.write_text("\n".join(labels) + "\n")
    print(f"  labels.txt: {len(labels)} tags")

    # Copy SentencePiece model
    sp_model_path = Path(tokenizer.vocab_file)
    shutil.copy2(sp_model_path, output_dir / "spiece.model")
    print(f"  spiece.model: {sp_model_path}")

    # Export verb-form-vocab.txt if available
    export_verb_dictionary(model, output_dir)

    # Round-trip tokenizer test
    test_text = "He go to school"
    encoded = tokenizer(test_text, return_tensors="pt")
    print(f"  Round-trip test: '{test_text}' -> {encoded['input_ids'].tolist()[0][:8]}...")

    # ONNX export
    print("Exporting to ONNX...")
    dummy_input = tokenizer("This is a test.", return_tensors="pt")
    input_ids = dummy_input["input_ids"]
    attention_mask = dummy_input["attention_mask"]

    onnx_path = output_dir / "model.onnx"
    torch.onnx.export(
        model,
        (input_ids, attention_mask),
        str(onnx_path),
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "sequence"},
            "attention_mask": {0: "batch", 1: "sequence"},
            "logits": {0: "batch", 1: "sequence"},
        },
        opset_version=14,
    )
    print(f"  model.onnx: {onnx_path.stat().st_size / 1e6:.0f} MB")

    # Verify ONNX output
    verify_onnx(onnx_path, input_ids, attention_mask, model, len(labels))

    print(f"\nDone. Bundle ready at: {output_dir}")
    print(f"To provision: tar cjf {model_name}.tar.bz2 -C {output_dir.parent} {output_dir.name}")


def export_verb_dictionary(model, output_dir: Path):
    """Extract verb form dictionary from model config if available."""
    verb_dict = getattr(model.config, "verb_form_vocab", None)
    if not verb_dict:
        print("  verb-form-vocab.txt: not available in model config (skipped)")
        return

    path = output_dir / "verb-form-vocab.txt"
    with open(path, "w") as f:
        for word, forms in verb_dict.items():
            for form, inflected in forms.items():
                f.write(f"{word}\t{form}\t{inflected}\n")
    print(f"  verb-form-vocab.txt: {len(verb_dict)} entries")


def verify_onnx(onnx_path: Path, input_ids, attention_mask, torch_model, num_labels: int):
    """Verify ONNX output matches PyTorch output."""
    import onnxruntime as ort

    session = ort.InferenceSession(str(onnx_path))
    ort_inputs = {
        "input_ids": input_ids.numpy(),
        "attention_mask": attention_mask.numpy(),
    }
    ort_logits = session.run(["logits"], ort_inputs)[0]

    with torch.no_grad():
        torch_logits = torch_model(input_ids, attention_mask=attention_mask).logits.numpy()

    max_diff = abs(ort_logits - torch_logits).max()
    print(f"  ONNX verification: max diff = {max_diff:.6f} (shape: {ort_logits.shape})")
    assert ort_logits.shape[-1] == num_labels, (
        f"Output dim mismatch: {ort_logits.shape[-1]} != {num_labels}"
    )
    assert max_diff < 1e-4, f"ONNX output diverges from PyTorch: max diff {max_diff}"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export GECToR model to ONNX")
    parser.add_argument("--model", required=True, choices=list(MODELS.keys()))
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    export(args.model, args.output)
