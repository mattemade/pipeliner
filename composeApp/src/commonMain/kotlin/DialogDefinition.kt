data class DialogDefinition(
    val message: String,
    val optionA: DialogOption,
    val optionB: DialogOption,
)

data class DialogOption(val name: String, val action: () -> Unit)