import os
import httpx
import tiktoken
from dotenv import load_dotenv
from llama_index.core import VectorStoreIndex, SimpleDirectoryReader, Settings
from llama_index.llms.openai import OpenAI
from llama_index.core.embeddings import BaseEmbedding
from llama_index.core.base.llms.types import LLMMetadata, MessageRole

# ================= 1. 加载配置文件 =================
# 从 .env 文件加载配置
load_dotenv()

# 读取 API Key
MINIMAX_API_KEY = os.getenv("MINIMAX_API_KEY")
EMBEDDING_API_KEY = os.getenv("EMBEDDING_API_KEY")

# 验证配置
if not MINIMAX_API_KEY or not EMBEDDING_API_KEY:
    raise ValueError("请在 .env 文件中配置 MINIMAX_API_KEY 和 EMBEDDING_API_KEY")

# ================= 2. 配置 LLM (MiniMax 大语言模型) =================
class MiniMaxOpenAI(OpenAI):
    @property
    def metadata(self) -> LLMMetadata:
        return LLMMetadata(
            context_window=8192,
            num_output=self.max_tokens or 4096,
            is_chat_model=True,
            is_function_calling_model=True,
            model_name=self.model,
            system_role=MessageRole.SYSTEM,
        )

    @property
    def _tokenizer(self):
        return tiktoken.get_encoding("cl100k_base")


llm = MiniMaxOpenAI(
    model="MiniMax-M2.7",
    api_key=MINIMAX_API_KEY,
    api_base="https://api.minimaxi.com/v1",
    temperature=0.1,
    max_tokens=4096,
)

# ================= 3. 自定义百炼 Embedding =================
class DashScopeEmbeddings(BaseEmbedding):
    def _get_query_embedding(self, query: str) -> list[float]:
        return self._get_embedding(query)

    async def _aget_query_embedding(self, query: str) -> list[float]:
        return self._get_embedding(query)

    def _get_text_embedding(self, text: str) -> list[float]:
        return self._get_embedding(text)

    async def _aget_text_embedding(self, text: str) -> list[float]:
        return self._get_embedding(text)

    def _get_embedding(self, text: str) -> list[float]:
        # Embedding API 使用标准端点和独立的 API Key
        url = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"
        headers = {
            "Authorization": f"Bearer {EMBEDDING_API_KEY}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": "text-embedding-v2",
            "input": {"texts": [text]}
        }
        with httpx.Client(timeout=60.0) as client:
            response = client.post(url, headers=headers, json=payload)
            if response.status_code != 200:
                print(f"Error Response: {response.text}")
            response.raise_for_status()
            result = response.json()
            return result["output"]["embeddings"][0]["embedding"]

    def _get_text_embeddings(self, texts: list[str]) -> list[list[float]]:
        # Embedding API 使用标准端点和独立的 API Key
        url = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"
        headers = {
            "Authorization": f"Bearer {EMBEDDING_API_KEY}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": "text-embedding-v2",
            "input": {"texts": texts}
        }
        with httpx.Client(timeout=120.0) as client:
            response = client.post(url, headers=headers, json=payload)
            if response.status_code != 200:
                print(f"Error Response: {response.text}")
            response.raise_for_status()
            result = response.json()
            return [e["embedding"] for e in result["output"]["embeddings"]]

embed_model = DashScopeEmbeddings()

# ================= 4. 全局设置 =================
Settings.llm = llm
Settings.embed_model = embed_model

# ================= 5. 加载文档数据 =================
print("正在加载文档...")
documents = SimpleDirectoryReader("./data").load_data()

# ================= 6. 构建向量存储索引 =================
print("正在构建索引（百炼 Embedding）...")
index = VectorStoreIndex.from_documents(documents)

# ================= 7. 查询与生成 =================
query_engine = index.as_query_engine()
question = "请简述文档中的核心内容"
print(f"问题: {question}\n")
response = query_engine.query(question)
print(f"回答: {response}")
