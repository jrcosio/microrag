"""Gemma with page images and EmbeddingGemma 2 with images on the desktop (F18 research R1).

The Python wheel does not expose Gemma's visual token budget; the native library does
(`litert_lm_conversation_optional_args_set_visual_token_budget`, c/conversation.h of v0.18.1), so the
conversation's optional arguments are hooked and the budget is set with ctypes while a block is open.
Every message sends max_output_tokens, so the optional arguments always exist.
"""

import contextlib
import ctypes
import time
from dataclasses import dataclass

from eval.generate import GREEDY

# Vision signatures of gemma-4-E2B-it.litertlm (ModelInfo, research R2); 280 is its maximum.
GEMMA_BUDGETS = (70, 140, 280)
# Vision signatures of embeddinggemma-2-text-vision-440m.litertlm.
EMBEDDER_TOKENS = (70, 140)
# Gemma's window with pages: the visual corpus is full of figures, so its D-034 text costs about 2.2 times its
# estimate (3,471 real tokens for 1,542), and three pages in tiles add 1,680. The text path keeps 4,096.
MAX_NUM_TOKENS = 8192

_current_budget = None


@dataclass(frozen=True)
class Text:
    text: str


@dataclass(frozen=True)
class Image:
    data: bytes


def _hooked(original):
    def create_optional_args(self, *args, **kwargs):
        pointer = original(self, *args, **kwargs)
        if _current_budget is not None and pointer:
            setter = self._lib.litert_lm_conversation_optional_args_set_visual_token_budget
            setter.argtypes = [ctypes.c_void_p, ctypes.c_int]
            setter.restype = None
            setter(pointer, _current_budget)
        return pointer

    create_optional_args.__wrapped__ = original
    return create_optional_args


def install_budget_hook(conversation_class):
    """Wraps the conversation's optional arguments once, so [visual_budget] reaches the native call."""
    current = conversation_class._create_optional_args
    if not hasattr(current, "__wrapped__"):
        conversation_class._create_optional_args = _hooked(current)


@contextlib.contextmanager
def visual_budget(budget):
    """Gemma's visual tokens per image inside the block; None keeps the file's default (280)."""
    global _current_budget
    if budget is not None and budget not in GEMMA_BUDGETS:
        raise ValueError(f"Gemma's file offers {GEMMA_BUDGETS} visual tokens, not {budget}")
    previous, _current_budget = _current_budget, budget
    try:
        yield
    finally:
        _current_budget = previous


def contents(parts):
    """litert_lm Contents with the parts in order: labels, images and text (v5-visual layout)."""
    from litert_lm import Content, Contents

    built = [Content.Text(part.text) if isinstance(part, Text) else Content.ImageBytes(part.data) for part in parts]
    return Contents(built)


def image_count(parts):
    return sum(isinstance(part, Image) for part in parts)


def open_gemma(model_path, max_num_images, backend="gpu", cache_dir=None):
    """Gemma with its text on [backend] and its vision encoder on the CPU, the only backend the file offers."""
    from litert_lm import Backend, Engine, conversation

    install_budget_hook(conversation.Conversation)
    chosen = Backend.GPU() if backend == "gpu" else Backend.CPU()
    return Engine(str(model_path), backend=chosen, vision_backend=Backend.CPU(), max_num_images=max_num_images,
                  max_num_tokens=MAX_NUM_TOKENS, cache_dir=str(cache_dir) if cache_dir else None, enable_benchmark=True)


def generate(engine, parts, budget, max_output_tokens):
    """One greedy answer to [parts]; returns the text, the first-token time and the prefill figures."""
    from litert_lm import SamplerConfig

    with visual_budget(budget):
        conversation = engine.create_conversation(sampler_config=SamplerConfig(**GREEDY))
        try:
            started = time.time()
            first = None
            pieces = []
            for chunk in conversation.send_message_async(contents(parts), max_output_tokens=max_output_tokens):
                if first is None:
                    first = time.time() - started
                content = chunk.get("content", []) if isinstance(chunk, dict) else []
                pieces.extend(part.get("text", "") for part in content if isinstance(part, dict))
            total = time.time() - started
            info = conversation.get_benchmark_info()
        finally:
            conversation.close()
    return {
        "answer": "".join(pieces),
        "ttft_s": round(first if first is not None else total, 3),
        "seconds": round(total, 3),
        "prefill_tokens": info.last_prefill_token_count,
        "prefill_tps": round(info.last_prefill_tokens_per_second, 1),
    }


def open_image_embedder(model_path, tokens, cache_dir=None):
    """The 440M on the CPU, for text and images, with [tokens] vision tokens per image."""
    from litert_lm import Backend, EmbeddingEngine

    if tokens not in EMBEDDER_TOKENS:
        raise ValueError(f"The 440M offers {EMBEDDER_TOKENS} tokens per image, not {tokens}")
    return EmbeddingEngine(str(model_path), backend=Backend.CPU(), vision_backend=Backend.CPU(),
                           vision_tokens_per_image=tokens, cache_dir=str(cache_dir) if cache_dir else None)


def embed_image(engine, data, tokens):
    """The raw 768 vector of an encoded image; truncation and L2 are done by the caller (R5)."""
    from litert_lm import Content
    from litert_lm.embedding_engine import EmbeddingOptions

    options = EmbeddingOptions(normalize=False, vision_tokens_per_image=tokens)
    return list(engine.compute_embedding([Content.ImageBytes(data)], options).embedding)
