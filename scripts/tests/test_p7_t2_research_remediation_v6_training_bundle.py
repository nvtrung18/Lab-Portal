import importlib.util
import hashlib
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = (
    ROOT / "scripts" / "finalize-p7-t1c-research-remediation-governance-v6.py"
)
REQUEST_IDENTITY = (
    "2f5e30613ec08a5ff14444cc45dfe4e35b2daae8bcea728e64e54785548a75c8"
)
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY"
DATASET_IDENTITY = (
    "7a0c264196889beb0c91414cd10195681df895073dc7ce3aeef586123de751c1"
)
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)
PIPELINE_PATH = ROOT / "scripts" / "training-pipeline-p7-t2-remediation-v6.py"
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation-v6.py"
BUILDER_PATH = ROOT / "scripts" / "build-p7-t2-research-remediation-v6-bundle.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t2-research-remediation-v6-bundle.py"
CONFIG_PATH = ROOT / "config" / "p7-t2-training-pipeline-t4-remediation-v6.json"
DATASET_MANIFEST = (
    ROOT / "datasets" / "p7-research-synthetic-training-dataset-v6"
    / "manifest.approved.json"
)
PROMPT_PROFILE_PATH = (
    ROOT / "config" / "p7-t4-research-remediation-governance-v6"
    / "research-prompt-profile-v3.approved.json"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T2ResearchRemediationV6TrainingGovernanceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t1c_remediation_finalizer_v6", FINALIZER_PATH)

    def test_finalizer_binds_exact_request_dataset_and_prompt_profile(self):
        documents = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by=APPROVAL_AUTHORITY,
            approved_at="2026-08-27T07:17:48Z",
        )
        self.finalizer.validate_documents(documents)

        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]
        self.assertEqual(REQUEST_IDENTITY, approval["requestIdentity"])
        self.assertEqual(DATASET_IDENTITY, approval["datasetIdentity"])
        self.assertEqual(PROMPT_PROFILE_IDENTITY, approval["promptProfileIdentity"])
        self.assertTrue(approval["authorization"]["externalTrainingAllowed"])
        self.assertFalse(approval["authorization"]["evaluationAllowed"])
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertFalse(approval["authorization"]["runtimeNormalizationAllowed"])
        self.assertFalse(approval["authorization"]["constrainedDecodingAllowed"])
        self.assertEqual(
            [f"RESEARCH_UC_{number:03d}" for number in range(1, 7)],
            approval["scope"]["includedUseCases"],
        )
        self.assertTrue(approval["scope"]["freshBaseModelStartRequired"])
        self.assertEqual(
            "CANDIDATE_ONLY",
            approval["scope"]["candidateDispositionAfterTraining"],
        )

        manifest = documents[self.finalizer.APPROVED_MANIFEST_REFERENCE]
        self.assertTrue(manifest["trainingAuthorized"])
        self.assertEqual("APPROVED", manifest["approval_status"])
        self.assertEqual(
            {"evaluation": 48, "train": 288, "validation": 48},
            manifest["recordCounts"],
        )
        self.assertEqual(
            manifest["checksum"],
            self.finalizer.artifact_identity(manifest, "checksum"),
        )

    def test_finalizer_rejects_any_other_request_identity(self):
        with self.assertRaisesRegex(self.finalizer.FinalizationError, "request identity"):
            self.finalizer.build_documents(
                request_identity="0" * 64,
                approved_by=APPROVAL_AUTHORITY,
                approved_at="2026-08-27T07:17:48Z",
            )

    def test_checked_in_approval_reproduces_byte_for_byte(self):
        approval = json.loads(
            (ROOT / self.finalizer.TRAINING_APPROVAL_REFERENCE).read_text(
                encoding="utf-8"
            )
        )
        documents = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by=APPROVAL_AUTHORITY,
            approved_at=approval["approval"]["approvedAt"],
        )
        for reference, document in documents.items():
            self.assertEqual(
                self.finalizer.json_bytes(document),
                (ROOT / reference).read_bytes(),
            )


class FakeTokenizer:
    eos_token_id = 99

    def apply_chat_template(self, messages, *, tokenize, add_generation_prompt):
        self.assertions = (tokenize, add_generation_prompt)
        if len(messages) == 2:
            return [10, 11]
        return [10, 11, 20, 21, self.eos_token_id]


class P7T2ResearchRemediationV6TrainingBundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pipeline = load_module("p7_t2_remediation_pipeline_v6", PIPELINE_PATH)
        cls.backend = load_module("p7_t2_remediation_backend_v6", BACKEND_PATH)
        cls.builder = load_module("p7_t2_remediation_bundle_builder_v6", BUILDER_PATH)
        cls.validator = load_module("p7_t2_remediation_bundle_validator_v6", VALIDATOR_PATH)
        cls.config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        cls.profile = json.loads(PROMPT_PROFILE_PATH.read_text(encoding="utf-8"))

    def test_config_dataset_and_prompt_profile_gates_pass(self):
        self.pipeline.validate_training_config(self.config)
        gates = self.pipeline.validate_dataset_and_contract_gates(
            DATASET_MANIFEST,
            self.config,
            ROOT,
        )
        self.assertEqual(
            {"train": 288, "validation": 48, "evaluation": 48},
            gates["counts"],
        )
        self.assertEqual(PROMPT_PROFILE_IDENTITY, gates["promptProfile"]["identity"])
        self.assertFalse(gates["runtimeControls"]["runtimeNormalizationAllowed"])
        self.assertFalse(gates["runtimeControls"]["constrainedDecodingAllowed"])

    def test_training_messages_use_exact_approved_research_system_instruction(self):
        record = json.loads(
            (DATASET_MANIFEST.parent / "train.jsonl")
            .read_text(encoding="utf-8")
            .splitlines()[0]
        )
        messages = self.backend.training_messages(record)
        expected = self.profile["assistantProfiles"]["RESEARCH_ASSISTANT"][
            "systemInstruction"
        ]
        self.assertEqual(expected, messages[0]["content"])
        self.assertEqual(
            self.backend.canonical_bytes(record["trainingTarget"]).decode("utf-8"),
            messages[2]["content"],
        )

    def test_tokenization_supervises_exactly_one_terminal_eos(self):
        record = json.loads(
            (DATASET_MANIFEST.parent / "train.jsonl")
            .read_text(encoding="utf-8")
            .splitlines()[0]
        )
        dataset, metrics = self.backend.tokenize_records_with_eos(
            [record], FakeTokenizer()
        )
        supervised = [value for value in dataset[0]["labels"] if value != -100]
        self.assertEqual(99, supervised[-1])
        self.assertEqual(1, supervised.count(99))
        self.assertEqual(5, metrics["maximumTokens"])

    def test_bundle_is_deterministic_valid_and_weight_free(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first" / self.builder.BUNDLE_NAME
            second = Path(directory) / "second" / self.builder.BUNDLE_NAME
            first_zip = first.parent / f"{self.builder.BUNDLE_NAME}.zip"
            second_zip = second.parent / f"{self.builder.BUNDLE_NAME}.zip"
            kwargs = {
                "source_root": ROOT,
                "source_commit": "e53daff90a2f12f1dd9af28e7c4a498e3083a409",
                "enforce_committed_sources": False,
            }
            first_manifest = self.builder.build_bundle(
                output_dir=first, zip_path=first_zip, **kwargs
            )
            second_manifest = self.builder.build_bundle(
                output_dir=second, zip_path=second_zip, **kwargs
            )
            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(first_zip.read_bytes(), second_zip.read_bytes())
            self.assertEqual(
                hashlib.sha256(first_zip.read_bytes()).hexdigest(),
                hashlib.sha256(second_zip.read_bytes()).hexdigest(),
            )
            self.assertEqual(first_manifest, self.validator.validate_bundle(first))
            paths = {item["path"] for item in first_manifest["fileInventory"]}
            self.assertIn(
                "datasets/p7-research-synthetic-training-dataset-v6/train.jsonl",
                paths,
            )
            self.assertIn(
                "config/p7-t4-research-remediation-governance-v6/"
                "research-prompt-profile-v3.approved.json",
                paths,
            )
            self.assertFalse(
                any(path.endswith((".safetensors", ".bin", ".pt", ".ckpt")) for path in paths)
            )


if __name__ == "__main__":
    unittest.main()
