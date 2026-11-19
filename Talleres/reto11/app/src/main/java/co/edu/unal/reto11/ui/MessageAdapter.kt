package co.edu.unal.reto11.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.reto11.R
import co.edu.unal.reto11.data.ChatMessage
import co.edu.unal.reto11.data.Role
import co.edu.unal.reto11.databinding.ItemMessageModelBinding
import co.edu.unal.reto11.databinding.ItemMessageUserBinding

/** Lista de burbujas del chat: a la derecha los mensajes del usuario, a la izquierda los del bot. */
class MessageAdapter(
    private val avatar: () -> String,
    private val onCopy: (String) -> Unit,
    private val onRetry: () -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) =
        if (getItem(position).role == Role.USER) TYPE_USER else TYPE_MODEL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            ModelHolder(ItemMessageModelBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserHolder -> holder.bind(message)
            is ModelHolder -> holder.bind(message)
        }
    }

    inner class UserHolder(private val b: ItemMessageUserBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(message: ChatMessage) {
            b.text.text = message.text
            b.bubble.setOnLongClickListener { onCopy(message.text); true }
        }
    }

    inner class ModelHolder(private val b: ItemMessageModelBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(message: ChatMessage) {
            val context = b.root.context
            b.avatar.text = avatar()

            val waiting = message.isStreaming && message.text.isEmpty()
            b.typing.visibility = if (waiting) View.VISIBLE else View.GONE
            b.text.visibility = if (waiting) View.GONE else View.VISIBLE

            if (message.isError) {
                b.text.text = message.text
                b.text.setTextColor(ContextCompat.getColor(context, R.color.error))
                b.bubble.setCardBackgroundColor(ContextCompat.getColor(context, R.color.error_container))
            } else {
                val codeBg = ContextCompat.getColor(context, R.color.code_background)
                val rendered = Markdown.render(message.text, codeBg)
                // Mientras se genera se muestra un cursor al final del texto.
                b.text.text = if (message.isStreaming) android.text.TextUtils.concat(rendered, " ▍") else rendered
                b.text.setTextColor(ContextCompat.getColor(context, R.color.on_surface))
                b.bubble.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bubble_model))
            }

            val finished = !message.isStreaming
            b.actions.visibility = if (finished) View.VISIBLE else View.GONE
            b.copyButton.visibility = if (finished && !message.isError) View.VISIBLE else View.GONE
            b.retryButton.visibility = if (finished && message.isError) View.VISIBLE else View.GONE
            b.copyButton.setOnClickListener { onCopy(message.text) }
            b.retryButton.setOnClickListener { onRetry() }
            b.bubble.setOnLongClickListener { onCopy(message.text); true }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id
        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
        // Sin payload la animación de cambio parpadea en cada fragmento; con uno se rebindea sin animar.
        override fun getChangePayload(old: ChatMessage, new: ChatMessage): Any = true
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_MODEL = 1
    }
}
