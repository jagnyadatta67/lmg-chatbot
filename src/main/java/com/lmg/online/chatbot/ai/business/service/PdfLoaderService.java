package com.lmg.online.chatbot.ai.business.service;
import com.lmg.online.chatbot.ai.business.docreader.SimpleInMemoryVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public class PdfLoaderService implements CommandLineRunner {

    private final SimpleInMemoryVectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    @Value("${pdf.file.path:/Users/jagnyapanigrahi/Documents/LMG_CHATBOT/pd/con.pdf}")
    private Resource pdfResource;

    public PdfLoaderService(SimpleInMemoryVectorStore vectorStore,
                            TokenTextSplitter textSplitter) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
    }

    @Override
    public void run(String... args) throws Exception {
        loadPdfToVectorStore();
    }

    public void loadPdfToVectorStore() throws IOException {
        System.out.println("Loading PDF into vector store...");




        try {
            String filePath = "/Users/jagnyapanigrahi/Documents/LMG_CHATBOT/pd/con.pdf";

            FileSystemResource resource = new FileSystemResource(filePath);
            if (!resource.exists()) {
                throw new FileNotFoundException("PDF not found: " + filePath);
            }

            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);


            List<Document> documents = pdfReader.get();

            // Split into chunks
            List<Document> splitDocuments = textSplitter.apply(documents);

            // Add to vector store
            vectorStore.add(splitDocuments);

            System.out.println("PDF loaded successfully. Total chunks: " + splitDocuments.size());

        } catch (Exception e) {
            e.printStackTrace();
        }



    }
}