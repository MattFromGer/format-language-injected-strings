package com.github.mattfromger.formatlanguageinjectedstrings.listeners

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil


class ReformatInjectedCodeListener : AnActionListener {
    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        if (action.javaClass.simpleName != "ReformatCodeAction") return

        val project: Project = event.project ?: return
        val psiFileOriginal: PsiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
        val documentManager = PsiDocumentManager.getInstance(project)
        val codeStyleManger = CodeStyleManager.getInstance(project)

        ApplicationManager.getApplication().invokeLater {
            PsiTreeUtil.findChildrenOfType(psiFileOriginal, PsiLanguageInjectionHost::class.java)
                .flatMap { languageInjectionHost ->
                    injectedLanguageManager.getInjectedPsiFiles(languageInjectionHost)
                        ?: emptyList()
                }
                .mapNotNull { pair -> pair.first.containingFile }
                .map { psiFile ->
                    val newPsiFile =
                        PsiFileFactory.getInstance(project).createFileFromText(psiFile.language, psiFile.text)

                    Pair(psiFile, newPsiFile)
                }.onEach { (psiFile, newPsiFile) ->
                    val psiDocument = documentManager.getDocument(psiFile) ?: return@onEach
                    WriteCommandAction.runWriteCommandAction(project) {
                        codeStyleManger.reformat(newPsiFile)

                        psiDocument.setText("\n".plus(newPsiFile.text))
                    }

                    thisLogger().info("Reformatted injected code from ${psiFile.text} to ${newPsiFile.text}")
                }
        }
    }
}

