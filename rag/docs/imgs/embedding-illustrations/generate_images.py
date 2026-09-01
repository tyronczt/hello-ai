#!/usr/bin/env python3
"""百炼图片生成脚本 - 使用 DashScope API"""

import json
import os
import time

import requests

API_KEY = os.getenv("DASHSCOPE_API_KEY")
if not API_KEY:
    raise RuntimeError("请先设置环境变量 DASHSCOPE_API_KEY")

# 尝试多个可能的 endpoint
ENDPOINTS = [
    "https://dashscope.aliyuncs.com/compatible-mode/v1/images/generations",
    "https://coding.dashscope.aliyuncs.com/v1/images/generations",
    "https://dashscope.aliyuncs.com/v1/images/generations",
]


def generate_image(prompt, output_path, model="wanx-v1"):
    """生成单张图片"""
    for api_url in ENDPOINTS:
        print(f"Trying endpoint: {api_url}")

        headers = {
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
        }

        payload = {
            "model": model,
            "prompt": prompt,
            "n": 1,
            "size": "1024*1024",
        }

        try:
            response = requests.post(api_url, headers=headers, json=payload, timeout=120)
            print(f"Response {response.status_code}: {response.text[:300]}")

            if response.status_code == 200:
                result = response.json()

                if "data" in result and len(result["data"]) > 0:
                    image_url = result["data"][0].get("url") or result["data"][0].get("b64_json")

                    if image_url:
                        if image_url.startswith("http"):
                            img_response = requests.get(image_url, timeout=60)
                            if img_response.status_code == 200:
                                with open(output_path, "wb") as f:
                                    f.write(img_response.content)
                                print(f"[OK] Saved: {output_path}")
                                return True
                        else:
                            import base64

                            img_data = base64.b64decode(image_url)
                            with open(output_path, "wb") as f:
                                f.write(img_data)
                            print(f"[OK] Saved: {output_path}")
                            return True
            elif response.status_code != 404:
                print(f"Non-404 error: {response.status_code}")
                break
        except Exception as e:
            print(f"Exception: {e}")

        time.sleep(0.5)

    return False


if __name__ == "__main__":
    output_dir = "d:/github/hello-ai/rag/docs/imgs/embedding-illustrations"
    os.makedirs(output_dir, exist_ok=True)

    # 4张配图的 prompt
    images = [
        {
            "name": "01-infographic-embedding-concept.png",
            "prompt": "Scientific infographic explaining vector embedding concept in Chinese. 2D semantic space visualization with labeled points: '苹果手机' and 'iPhone' close together, '你好' and 'hello' close together, '苹果手机' far from '水果苹果'. Text to Vector transformation arrows. Macaron pastel colors (lavender, mint, peach). Blueprint scientific style, geometric precision.",
        },
        {
            "name": "02-infographic-similarity-metrics.png",
            "prompt": "Scientific infographic comparing three similarity metrics for vector search in Chinese. Three cards: 1.Cosine Similarity with formula and angle diagram, 2.Dot Product, 3.Euclidean Distance. Macaron pastel colors. Scientific blueprint style.",
        },
        {
            "name": "03-infographic-rag-flow.png",
            "prompt": "Horizontal flowchart showing RAG retrieval pipeline in Chinese. 5 steps: 1.用户问题 2.Embedding模型 3.向量数据库 4.Top-K召回 5.LLM生成答案. Arrows connecting steps. Macaron pastel colors.",
        },
        {
            "name": "04-infographic-ann-algorithms.png",
            "prompt": "Three-column comparison for ANN algorithms in Chinese: 1.IVF clustering, 2.HNSW graph, 3.LSH hash. Highlight HNSW. Macaron pastel colors.",
        },
    ]

    models = ["wanx-v1", "wan2.6", "qwen-vl-plus"]

    for img in images:
        output_path = os.path.join(output_dir, img["name"])
        success = False

        for model in models:
            print(f"\n=== Trying model: {model} ===")
            success = generate_image(img["prompt"], output_path, model)
            if success:
                break
            time.sleep(1)

        if success:
            print(f"[OK] {img['name']} generated!")
        else:
            print(f"[FAIL] {img['name']} failed!")

    print("\nAll done!")
