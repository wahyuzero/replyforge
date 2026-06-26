package com.wahyuzero.replyforge.ui.main

import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.db.RuleDao
import com.wahyuzero.replyforge.data.model.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var ruleDao: RuleDao
    private lateinit var viewModel: RulesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = mock()
        ruleDao = mock()
        whenever(db.ruleDao()).thenReturn(ruleDao)
        whenever(ruleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        viewModel = RulesViewModel(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty rules`() = runTest {
        assertThat(viewModel.rules.value, empty())
    }

    @Test
    fun `rules are loaded from DAO`() = runTest {
        val testRules = listOf(
            Rule(id = 1, name = "Rule 1", pattern = "hello", response = "Hi"),
            Rule(id = 2, name = "Rule 2", pattern = "bye", response = "Goodbye")
        )
        whenever(ruleDao.getAllRules()).thenReturn(flowOf(testRules))
        // Recreate VM to pick up new mock
        viewModel = RulesViewModel(db)

        assertThat(viewModel.rules.value, equalTo(testRules))
    }

    @Test
    fun `toggleRule calls DAO setEnabled`() = runTest {
        val rule = Rule(id = 5, name = "Test", pattern = "test", response = "resp")
        viewModel.toggleRule(rule, false)
        verify(ruleDao).setEnabled(5, false)
    }

    @Test
    fun `deleteRule calls DAO delete`() = runTest {
        val rule = Rule(id = 3, name = "Test", pattern = "test", response = "resp")
        viewModel.deleteRule(rule)
        verify(ruleDao).delete(rule)
    }
}
